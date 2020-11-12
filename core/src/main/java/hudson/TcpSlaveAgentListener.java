/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayInputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import edu.umd.cs.findbugs.annotations.Nullable;

import hudson.model.AperiodicWork;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import jenkins.model.identity.InstanceIdentityProvider;
import jenkins.security.stapler.StaplerAccessibleType;
import jenkins.slaves.RemotingVersionInfo;
import jenkins.util.SystemProperties;
import hudson.slaves.OfflineCause;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import jenkins.AgentProtocol;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Listens to incoming TCP connections, for example from agents.
 *
 * <p>
 * Aside from the HTTP endpoint, Jenkins runs {@link TcpSlaveAgentListener} that listens on a TCP socket.
 * Historically  this was used for inbound connection from agents (hence the name), but over time
 * it was extended and made generic, so that multiple protocols of different purposes can co-exist on the
 * same socket.
 *
 * <p>
 * This class accepts the socket, then after a short handshaking, it dispatches to appropriate
 * {@link AgentProtocol}s.
 *
 * @author Kohsuke Kawaguchi
 * @see AgentProtocol
 */
@StaplerAccessibleType
public final class TcpSlaveAgentListener extends Thread {

    private final ServerSocketChannel serverSocket;
    private volatile boolean shuttingDown;

    public final int configuredPort;

    /**
     * @param port
     *      Use 0 to choose a random port.
     */
    public TcpSlaveAgentListener(int port) throws IOException {
        super("TCP agent listener port="+port);
        try {
            serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(port));
        } catch (BindException e) {
            throw (BindException)new BindException("Failed to listen on port "+port+" because it's already in use.").initCause(e);
        }
        this.configuredPort = port;
        setUncaughtExceptionHandler((t, e) -> {
            LOGGER.log(Level.SEVERE, "Uncaught exception in TcpSlaveAgentListener " + t + ", attempting to reschedule thread", e);
            shutdown();
            TcpSlaveAgentListenerRescheduler.schedule(t, e);
        });

        LOGGER.log(Level.FINE, "TCP agent listener started on port {0}", getPort());

        start();
    }

    /**
     * Gets the TCP port number in which we are listening.
     */
    public int getPort() {
        return serverSocket.socket().getLocalPort();
    }

    /**
     * Gets the TCP port number in which we are advertising.
     * @since 1.656
     */
    public int getAdvertisedPort() {
        return CLI_PORT != null ? CLI_PORT : getPort();
    }

    /**
     * Gets the host name that we advertise protocol clients to connect to.
     * @since 2.198
     */
    public String getAdvertisedHost() {
        if (CLI_HOST_NAME != null) {
          return CLI_HOST_NAME;
        }
        try {
            return new URL(Jenkins.get().getRootUrl()).getHost();
        } catch (MalformedURLException | NullPointerException e) {
            throw new IllegalStateException("Could not get TcpSlaveAgentListener host name", e);
        }
    }

    /**
     * Gets the Base64 encoded public key that forms part of this instance's identity keypair.
     * @return the Base64 encoded public key
     * @since 2.16
     */
    @Nullable
    public String getIdentityPublicKey() {
        RSAPublicKey key = InstanceIdentityProvider.RSA.getPublicKey();
        return key == null ? null : Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Returns a comma separated list of the enabled {@link AgentProtocol#getName()} implementations so that
     * clients can avoid creating additional work for the server attempting to connect with unsupported protocols.
     *
     * @return a comma separated list of the enabled {@link AgentProtocol#getName()} implementations
     * @since 2.16
     */
    public String getAgentProtocolNames() {
        return StringUtils.join(Jenkins.get().getAgentProtocols(), ", ");
    }

    /**
     * Gets Remoting minimum supported version to prevent unsupported agents from connecting
     * @since 2.171
     */
    public VersionNumber getRemotingMinimumVersion() {
        return RemotingVersionInfo.getMinimumSupportedVersion();
    }

    @Override
    public void run() {
        try {
            // the loop eventually terminates when the socket is closed.
            while (!shuttingDown) {
                Socket s = serverSocket.accept().socket();

                // this prevents a connection from silently terminated by the router in between or the other peer
                // and that goes without unnoticed. However, the time out is often very long (for example 2 hours
                // by default in Linux) that this alone is enough to prevent that.
                s.setKeepAlive(true);
                // we take care of buffering on our own
                s.setTcpNoDelay(true);

                new ConnectionHandler(s, new ConnectionHandlerFailureCallback(this) {
                    @Override
                    public void run(Throwable cause) {
                        LOGGER.log(Level.WARNING, "Connection handler failed, restarting listener", cause);
                        shutdown();
                        TcpSlaveAgentListenerRescheduler.schedule(getParentThread(), cause);
                    }
                }).start();
            }
        } catch (IOException e) {
            if(!shuttingDown) {
                LOGGER.log(Level.SEVERE,"Failed to accept TCP connections", e);
            }
        }
    }

    /**
     * Initiates the shuts down of the listener.
     */
    public void shutdown() {
        shuttingDown = true;
        try {
            SocketAddress localAddress = serverSocket.getLocalAddress();
            if (localAddress instanceof InetSocketAddress) {
                InetSocketAddress address = (InetSocketAddress) localAddress;
                Socket client = new Socket(address.getHostName(), address.getPort());
                client.setSoTimeout(1000); // waking the acceptor loop should be quick
                new PingAgentProtocol().connect(client);
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to send Ping to wake acceptor loop", e);
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close down TCP port",e);
        }
    }

    private final class ConnectionHandler extends Thread {
        private static final String DEFAULT_RESPONSE_404 = "HTTP/1.0 404 Not Found\r\n" +
                        "Content-Type: text/plain;charset=UTF-8\r\n" +
                        "\r\n" +
                        "Not Found\r\n";
        private final Socket s;
        /**
         * Unique number to identify this connection. Used in the log.
         */
        private final int id;

        public ConnectionHandler(Socket s, ConnectionHandlerFailureCallback parentTerminator) {
            this.s = s;
            synchronized(getClass()) {
                id = iotaGen++;
            }
            setName("TCP agent connection handler #"+id+" with "+s.getRemoteSocketAddress());
            setUncaughtExceptionHandler((t, e) -> {
                LOGGER.log(Level.SEVERE, "Uncaught exception in TcpSlaveAgentListener ConnectionHandler " + t, e);
                try {
                    s.close();
                    parentTerminator.run(e);
                } catch (IOException e1) {
                    LOGGER.log(Level.WARNING, "Could not close socket after unexpected thread death", e1);
                }
            });
        }

        @Override
        public void run() {
            try {
                LOGGER.log(Level.FINE, "Accepted connection #{0} from {1}", new Object[] {id, s.getRemoteSocketAddress()});

                DataInputStream in = new DataInputStream(s.getInputStream());

                // peek the first few bytes to determine what to do with this client
                byte[] head = new byte[10];
                in.readFully(head);

                String header = new String(head, StandardCharsets.US_ASCII);
                if (header.startsWith("GET ")) {
                    // this looks like an HTTP client
                    respondHello(header,s);
                    return;
                }

                // otherwise assume this is AgentProtocol and start from the beginning
                String s = new DataInputStream(new SequenceInputStream(new ByteArrayInputStream(head),in)).readUTF();

                if(s.startsWith("Protocol:")) {
                    String protocol = s.substring(9);
                    AgentProtocol p = AgentProtocol.of(protocol);
                    if (p!=null) {
                        if (Jenkins.get().getAgentProtocols().contains(protocol)) {
                            LOGGER.log(p instanceof PingAgentProtocol ? Level.FINE : Level.INFO, "Accepted {0} connection #{1} from {2}", new Object[] {protocol, id, this.s.getRemoteSocketAddress()});
                            p.handle(this.s);
                        } else {
                            error("Disabled protocol:" + s, this.s);
                        }
                    } else
                        error("Unknown protocol:", this.s);
                } else {
                    error("Unrecognized protocol: " + s, this.s);
                }
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING,"Connection #"+id+" aborted",e);
                try {
                    s.close();
                } catch (IOException ex) {
                    // try to clean up the socket
                }
            } catch (IOException e) {
                if (e instanceof EOFException) {
                    LOGGER.log(Level.INFO, "Connection #{0} failed: {1}", new Object[] {id, e});
                } else {
                    LOGGER.log(Level.WARNING, "Connection #" + id + " failed", e);
                }
                try {
                    s.close();
                } catch (IOException ex) {
                    // try to clean up the socket
                }
            }
        }

        /**
         * Respond to HTTP request with simple diagnostics.
         * Primarily used to test the low-level connectivity.
         */
        private void respondHello(String header, Socket s) throws IOException {
            try {
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                String response;
                if (header.startsWith("GET / ")) {
                    response = "HTTP/1.0 200 OK\r\n" +
                            "Content-Type: text/plain;charset=UTF-8\r\n" +
                            "\r\n" +
                            "Jenkins-Agent-Protocols: " + getAgentProtocolNames()+"\r\n" +
                            "Jenkins-Version: " + Jenkins.VERSION + "\r\n" +
                            "Jenkins-Session: " + Jenkins.SESSION_HASH + "\r\n" +
                            "Client: " + s.getInetAddress().getHostAddress() + "\r\n" +
                            "Server: " + s.getLocalAddress().getHostAddress() + "\r\n" +
                            "Remoting-Minimum-Version: " + getRemotingMinimumVersion() + "\r\n";
                } else {
                    response = DEFAULT_RESPONSE_404;
                }
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();
                s.shutdownOutput();

                InputStream i = s.getInputStream();
                IOUtils.copy(i, NullOutputStream.NULL_OUTPUT_STREAM);
                s.shutdownInput();
            } finally {
                s.close();
            }
        }

        private void error(String msg, Socket s) throws IOException {
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            String response = msg + System.lineSeparator();
            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
            s.shutdownOutput();
            LOGGER.log(Level.WARNING, "Connection #{0} is aborted: {1}", new Object[]{id, msg});
            s.close();
        }
    }

    // This is essentially just to be able to pass the parent thread into the callback, as it can't access it otherwise
    private static abstract class ConnectionHandlerFailureCallback {
        private Thread parentThread;

        public ConnectionHandlerFailureCallback(Thread parentThread) {
            this.parentThread = parentThread;
        }

        public Thread getParentThread() {
            return parentThread;
        }

        public abstract void run(Throwable cause);
    }

    /**
     * This extension provides a Ping protocol that allows people to verify that the TcpSlaveAgentListener is alive.
     * We also use this to wake the acceptor thread on termination.
     *
     * @since 1.653
     */
    @Extension
    @Symbol("ping")
    public static class PingAgentProtocol extends AgentProtocol {

        private final byte[] ping;

        public PingAgentProtocol() {
            ping = "Ping\n".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public String getName() {
            return "Ping";
        }

        @Override
        public String getDisplayName() {
            return Messages.TcpSlaveAgentListener_PingAgentProtocol_displayName();
        }

        @Override
        public void handle(Socket socket) throws IOException, InterruptedException {
            try {
                try (OutputStream stream = socket.getOutputStream()) {
                    LOGGER.log(Level.FINE, "Received ping request from {0}", socket.getRemoteSocketAddress());
                    stream.write(ping);
                    stream.flush();
                    LOGGER.log(Level.FINE, "Sent ping response to {0}", socket.getRemoteSocketAddress());
                }
            } finally {
                socket.close();
            }
        }

        public boolean connect(Socket socket) throws IOException {
            try {
                LOGGER.log(Level.FINE, "Requesting ping from {0}", socket.getRemoteSocketAddress());
                try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                    out.writeUTF("Protocol:Ping");
                    try (InputStream in = socket.getInputStream()) {
                        byte[] response = new byte[ping.length];
                        int responseLength = in.read(response);
                        if (responseLength == ping.length && Arrays.equals(response, ping)) {
                            LOGGER.log(Level.FINE, "Received ping response from {0}", socket.getRemoteSocketAddress());
                            return true;
                        } else {
                            LOGGER.log(Level.FINE, "Expected ping response from {0} of {1} got {2}", new Object[]{
                                    socket.getRemoteSocketAddress(),
                                    new String(ping, StandardCharsets.UTF_8),
                                    responseLength > 0 && responseLength <= response.length ?
                                        new String(response, 0, responseLength, StandardCharsets.UTF_8) :
                                        "bad response length " + responseLength
                            });
                            return false;
                        }
                    }
                }
            } finally {
                socket.close();
            }
        }
    }

    /**
     * Reschedules the {@code TcpSlaveAgentListener} on demand.  Disables itself after running.
     */
    @Extension
    @Restricted(NoExternalUse.class)
    public static class TcpSlaveAgentListenerRescheduler extends AperiodicWork {
        private Thread originThread;
        private Throwable cause;
        private long recurrencePeriod = 5000;
        private boolean isActive;

        public TcpSlaveAgentListenerRescheduler() {
            isActive = false;
        }

        public TcpSlaveAgentListenerRescheduler(Thread originThread, Throwable cause) {
            this.originThread = originThread;
            this.cause = cause;
            this.isActive = false;
        }

        public void setOriginThread(Thread originThread) {
            this.originThread = originThread;
        }

        public void setCause(Throwable cause) {
            this.cause = cause;
        }

        public void setActive(boolean active) {
            isActive = active;
        }

        @Override
        public long getRecurrencePeriod() {
            return recurrencePeriod;
        }

        @Override
        public AperiodicWork getNewInstance() {
            return new TcpSlaveAgentListenerRescheduler(originThread, cause);
        }

        @Override
        protected void doAperiodicRun() {
            if (isActive) {
                try {
                    if (originThread.isAlive()) {
                        originThread.interrupt();
                    }
                    int port = Jenkins.get().getSlaveAgentPort();
                    if (port != -1) {
                        new TcpSlaveAgentListener(port).start();
                        LOGGER.log(Level.INFO, "Restarted TcpSlaveAgentListener");
                    } else {
                        LOGGER.log(Level.SEVERE, "Uncaught exception in TcpSlaveAgentListener " + originThread + ". Port is disabled, not rescheduling", cause);
                    }
                    isActive = false;
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not reschedule TcpSlaveAgentListener - trying again.", cause);
                }
            }
        }

        public static void schedule(Thread originThread, Throwable cause) {
            schedule(originThread, cause,5000);
        }

        public static void schedule(Thread originThread, Throwable cause, long approxDelay) {
            TcpSlaveAgentListenerRescheduler rescheduler = AperiodicWork.all().get(TcpSlaveAgentListenerRescheduler.class);
            rescheduler.originThread = originThread;
            rescheduler.cause = cause;
            rescheduler.recurrencePeriod = approxDelay;
            rescheduler.isActive = true;
        }
    }


    /**
     * Connection terminated because we are reconnected from the current peer.
     */
    public static class ConnectionFromCurrentPeer extends OfflineCause {
        public String toString() {
            return "The current peer is reconnecting";
        }
    }

    private static int iotaGen=1;

    private static final Logger LOGGER = Logger.getLogger(TcpSlaveAgentListener.class.getName());

    /**
     * Host name that we advertise protocol clients to connect to.
     * This is primarily for those who have reverse proxies in place such that the HTTP host name
     * and the TCP/IP connection host names are different.
     * (Note: despite the name, this is used for any client, not only deprecated Remoting-based CLI.)
     * TODO: think about how to expose this (including whether this needs to be exposed at all.)
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    @Restricted(NoExternalUse.class)
    public static String CLI_HOST_NAME = SystemProperties.getString(TcpSlaveAgentListener.class.getName()+".hostName");

    /**
     * Port number that we advertise protocol clients to connect to.
     * This is primarily for the case where the port that Jenkins runs is different from the port
     * that external world should connect to, because of the presence of NAT / port-forwarding / TCP reverse
     * proxy.
     * (Note: despite the name, this is used for any client, not only deprecated Remoting-based CLI.)
     * If left to null, fall back to {@link #getPort()}
     *
     * @since 1.611
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    @Restricted(NoExternalUse.class)
    public static Integer CLI_PORT = SystemProperties.getInteger(TcpSlaveAgentListener.class.getName()+".port");
}
