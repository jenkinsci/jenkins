package jenkins.slaves;

import hudson.AbortException;
import hudson.Extension;
import hudson.TcpSlaveAgentListener;
import hudson.Util;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.SocketChannelStream;
import hudson.slaves.SlaveComputer;
import jenkins.AgentProtocol;
import jenkins.model.Jenkins;
import org.jenkinsci.remoting.engine.JnlpProtocol;
import org.jenkinsci.remoting.engine.JnlpProtocol3;
import org.jenkinsci.remoting.engine.jnlp3.ChannelCiphers;
import org.jenkinsci.remoting.engine.jnlp3.CipherUtils;
import org.jenkinsci.remoting.engine.jnlp3.HandshakeCiphers;
import org.jenkinsci.remoting.nio.NioChannelHub;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Master-side implementation for JNLP3-connect protocol.
 *
 * <p>@see {@link JnlpProtocol3} for more details.
 */
@Extension
public class JnlpSlaveAgentProtocol3 extends AgentProtocol {
    @Inject
    NioChannelSelector hub;

    @Override
    public String getName() {
        return "JNLP3-connect";
    }

    @Override
    public void handle(Socket socket) throws IOException, InterruptedException {
        new Handler(hub.getHub(), socket).run();
    }

    static class Handler extends JnlpSlaveHandshake {

        public Handler(NioChannelHub hub, Socket socket) throws IOException {
            super(hub,socket,
                    new DataInputStream(socket.getInputStream()),
                    new PrintWriter(new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true));
        }

        protected void run() throws IOException, InterruptedException {
            request.load(new ByteArrayInputStream(in.readUTF().getBytes("UTF-8")));
            String nodeName = request.getProperty(JnlpProtocol3.SLAVE_NAME_KEY);
            String encryptedChallenge = request.getProperty(JnlpProtocol3.CHALLENGE_KEY);
            byte[] handshakeSpecKey = CipherUtils.keyFromString(
                    request.getProperty(JnlpProtocol3.HANDSHAKE_SPEC_KEY));
            String cookie = request.getProperty(JnlpProtocol3.COOKIE_KEY);

            SlaveComputer computer = (SlaveComputer) Jenkins.getInstance().getComputer(nodeName);
            if(computer == null) {
                error("Slave trying to register for invalid node: " + nodeName);
                return;
            }
            String slaveSecret = computer.getJnlpMac();

            HandshakeCiphers handshakeCiphers = null;
            try {
                handshakeCiphers = HandshakeCiphers.create(nodeName, slaveSecret, handshakeSpecKey);
            } catch (Exception e) {
                error("Failed to create handshake ciphers for node: " + nodeName);
                return;
            }

            String challenge = null;
            try {
                challenge = handshakeCiphers.decrypt(encryptedChallenge);
            } catch (Exception e) {
                throw new IOException("Unable to decrypt challenge", e);
            }
            if (!challenge.startsWith(JnlpProtocol3.CHALLENGE_PREFIX)) {
                error("Received invalid challenge");
                return;
            }

            // At this point the slave looks legit, check if we think they are already connected.
            Channel oldChannel = computer.getChannel();
            if(oldChannel != null) {
                if (cookie != null && cookie.equals(oldChannel.getProperty(COOKIE_NAME))) {
                    // We think we are currently connected, but this request proves that it's from
                    // the party we are supposed to be communicating to. so let the current one get
                    // disconnected
                    LOGGER.info("Disconnecting " + nodeName +
                            " as we are reconnected from the current peer");
                    try {
                        computer.disconnect(new TcpSlaveAgentListener.ConnectionFromCurrentPeer())
                                .get(15, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        throw new IOException("Failed to disconnect the current client",e);
                    } catch (TimeoutException e) {
                        throw new IOException("Failed to disconnect the current client",e);
                    }
                } else {
                    error(nodeName +
                            " is already connected to this master. Rejecting this connection.");
                    return;
                }
            }

            // Send challenge response.
            String challengeReverse = new StringBuilder(
                    challenge.substring(JnlpProtocol3.CHALLENGE_PREFIX.length()))
                    .reverse().toString();
            String challengeResponse = JnlpProtocol3.CHALLENGE_PREFIX + challengeReverse;
            String encryptedChallengeResponse = null;
            try {
                encryptedChallengeResponse = handshakeCiphers.encrypt(challengeResponse);
            } catch (Exception e) {
                throw new IOException("Error encrypting challenge response", e);
            }
            out.println(encryptedChallengeResponse.getBytes("UTF-8").length);
            out.print(encryptedChallengeResponse);
            out.flush();

            // If the slave accepted our challenge response it will send channel cipher keys.
            String challengeVerificationMessage = in.readUTF();
            if (!challengeVerificationMessage.equals(JnlpProtocol.GREETING_SUCCESS)) {
                error("Slave did not accept our challenge response");
                return;
            }
            String encryptedAesKeyString = in.readUTF();
            String encryptedSpecKeyString = in.readUTF();
            ChannelCiphers channelCiphers = null;
            try {
                String aesKeyString = handshakeCiphers.decrypt(encryptedAesKeyString);
                String specKeyString = handshakeCiphers.decrypt(encryptedSpecKeyString);
                channelCiphers = ChannelCiphers.create(
                        CipherUtils.keyFromString(aesKeyString),
                        CipherUtils.keyFromString(specKeyString));
            } catch (Exception e) {
                error("Failed to decrypt channel cipher keys");
                return;
            }

            String newCookie = generateCookie();
            try {
                out.println(handshakeCiphers.encrypt(newCookie));
            } catch (Exception e) {
                throw new IOException("Error encrypting cookie", e);
            }

            Channel establishedChannel = jnlpConnect(computer, channelCiphers);
            establishedChannel.setProperty(COOKIE_NAME, newCookie);
        }

        protected Channel jnlpConnect(
                SlaveComputer computer, ChannelCiphers channelCiphers)
                throws InterruptedException, IOException {
            final String nodeName = computer.getName();
            final OutputStream log = computer.openLogFile();
            PrintWriter logw = new PrintWriter(log,true);
            logw.println("JNLP agent connected from "+ socket.getInetAddress());

            try {
                ChannelBuilder cb = createChannelBuilder(nodeName);
                Channel channel = cb.withHeaderStream(log)
                        .build(new CipherInputStream(SocketChannelStream.in(socket),
                                        channelCiphers.getDecryptCipher()),
                                new CipherOutputStream(SocketChannelStream.out(socket),
                                        channelCiphers.getEncryptCipher()));

                computer.setChannel(channel, log,
                        new Channel.Listener() {
                            @Override
                            public void onClosed(Channel channel, IOException cause) {
                                if(cause != null)
                                    LOGGER.log(Level.WARNING,
                                            Thread.currentThread().getName() + " for + " +
                                                    nodeName + " terminated", cause);
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    // Do nothing.
                                }
                            }
                        });
                return computer.getChannel();
            } catch (AbortException e) {
                logw.println(e.getMessage());
                logw.println("Failed to establish the connection with the slave");
                throw e;
            } catch (IOException e) {
                logw.println("Failed to establish the connection with the slave " + nodeName);
                e.printStackTrace(logw);
                throw e;
            }
        }

        private String generateCookie() {
            byte[] cookie = new byte[32];
            new SecureRandom().nextBytes(cookie);
            return Util.toHexString(cookie);
        }
    }

    static final String COOKIE_NAME = JnlpSlaveAgentProtocol3.class.getName() + ".cookie";
}
