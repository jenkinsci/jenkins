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

import java.nio.charset.Charset;
import java.security.interfaces.RSAPublicKey;
import javax.annotation.Nullable;
import jenkins.model.Jenkins;
import jenkins.model.identity.InstanceIdentityProvider;
import jenkins.util.SystemProperties;
import hudson.slaves.OfflineCause;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.util.Arrays;
import jenkins.AgentProtocol;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Listens to incoming TCP connections from JNLP agents and CLI.
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

        LOGGER.log(Level.FINE, "JNLP agent listener started on TCP port {0}", getPort());

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
     * Gets the Base64 encoded public key that forms part of this instance's identity keypair.
     * @return the Base64 encoded public key
     * @since 2.16
     */
    @Nullable
    public String getIdentityPublicKey() {
        RSAPublicKey key = InstanceIdentityProvider.RSA.getPublicKey();
        return key == null ? null : new String(Base64.encodeBase64(key.getEncoded()), Charset.forName("UTF-8"));
    }

    /**
     * Returns a comma separated list of the enabled {@link AgentProtocol#getName()} implementations so that
     * clients can avoid creating additional work for the server attempting to connect with unsupported protocols.
     *
     * @return a comma separated list of the enabled {@link AgentProtocol#getName()} implementations
     * @since FIXME
     */
    public String getAgentProtocolNames() {
        return StringUtils.join(Jenkins.getInstance().getAgentProtocols(), ", ");
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

                new ConnectionHandler(s).start();
            }
        } catch (IOException e) {
            if(!shuttingDown) {
                LOGGER.log(Level.SEVERE,"Failed to accept JNLP agent connections",e);
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
        private final Socket s;
        /**
         * Unique number to identify this connection. Used in the log.
         */
        private final int id;

        public ConnectionHandler(Socket s) {
            this.s = s;
            synchronized(getClass()) {
                id = iotaGen++;
            }
            setName("TCP agent connection handler #"+id+" with "+s.getRemoteSocketAddress());
        }

        @Override
        public void run() {
            try {
                LOGGER.log(Level.INFO, "Accepted connection #{0} from {1}", new Object[]{id,s.getRemoteSocketAddress()});

                DataInputStream in = new DataInputStream(s.getInputStream());
                PrintWriter out = new PrintWriter(
                        new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),"UTF-8")),
                        true); // DEPRECATED: newer protocol shouldn't use PrintWriter but should use DataOutputStream

                String s = in.readUTF();

                if(s.startsWith("Protocol:")) {
                    String protocol = s.substring(9);
                    AgentProtocol p = AgentProtocol.of(protocol);
                    if (p!=null) {
                        if (Jenkins.getInstance().getAgentProtocols().contains(protocol)) {
                            p.handle(this.s);
                        } else {
                            error(out, "Disabled protocol:" + s);
                        }
                    } else
                        error(out, "Unknown protocol:" + s);
                } else {
                    error(out, "Unrecognized protocol: "+s);
                }
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING,"Connection #"+id+" aborted",e);
                try {
                    s.close();
                } catch (IOException _) {
                    // try to clean up the socket
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,"Connection #"+id+" failed",e);
                try {
                    s.close();
                } catch (IOException _) {
                    // try to clean up the socket
                }
            }
        }

        private void error(PrintWriter out, String msg) throws IOException {
            out.println(msg);
            LOGGER.log(Level.WARNING,"Connection #"+id+" is aborted: "+msg);
            s.close();
        }
    }

    /**
     * This extension provides a Ping protocol that allows people to verify that the TcpSlaveAgentListener is alive.
     * We also use this to wake the acceptor thread on termination.
     *
     * @since 1.653
     */
    @Extension
    public static class PingAgentProtocol extends AgentProtocol {

        private final byte[] ping;

        public PingAgentProtocol() {
            try {
                ping = "Ping\n".getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("JLS mandates support for UTF-8 charset", e);
            }
        }

        /**
         * Allow essential {@link AgentProtocol} implementations (basically {@link PingAgentProtocol})
         * to be always enabled.
         *
         * @return {@code true} if the protocol can never be disbaled.
         * @since FIXME
         */
        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public String getName() {
            return "Ping";
        }

        @Override
        public void handle(Socket socket) throws IOException, InterruptedException {
            try {
                OutputStream stream = socket.getOutputStream();
                try {
                    LOGGER.log(Level.FINE, "Received ping request from {0}", socket.getRemoteSocketAddress());
                    stream.write(ping);
                    stream.flush();
                    LOGGER.log(Level.FINE, "Sent ping response to {0}", socket.getRemoteSocketAddress());
                } finally {
                    stream.close();
                }
            } finally {
                socket.close();
            }
        }

        public boolean connect(Socket socket) throws IOException {
            try {
                DataOutputStream out = null;
                InputStream in = null;
                try {
                    LOGGER.log(Level.FINE, "Requesting ping from {0}", socket.getRemoteSocketAddress());
                    out = new DataOutputStream(socket.getOutputStream());
                    out.writeUTF("Protocol:Ping");
                    in = socket.getInputStream();
                    byte[] response = new byte[ping.length];
                    int responseLength = in.read(response);
                    if (responseLength == ping.length && Arrays.equals(response, ping)) {
                        LOGGER.log(Level.FINE, "Received ping response from {0}", socket.getRemoteSocketAddress());
                        return true;
                    } else {
                        LOGGER.log(Level.FINE, "Expected ping response from {0} of {1} got {2}", new Object[]{
                                socket.getRemoteSocketAddress(),
                                new String(ping, "UTF-8"),
                                new String(response, 0, responseLength, "UTF-8")
                        });
                        return false;
                    }
                } finally {
                    IOUtils.closeQuietly(out);
                    IOUtils.closeQuietly(in);
                }
            } finally {
                socket.close();
            }
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
     * Host name that we advertise the CLI client to connect to.
     * This is primarily for those who have reverse proxies in place such that the HTTP host name
     * and the CLI TCP/IP connection host names are different.
     *
     * TODO: think about how to expose this (including whether this needs to be exposed at all.)
     */
    public static String CLI_HOST_NAME = SystemProperties.getString(TcpSlaveAgentListener.class.getName()+".hostName");

    /**
     * Port number that we advertise the CLI client to connect to.
     * This is primarily for the case where the port that Jenkins runs is different from the port
     * that external world should connect to, because of the presence of NAT / port-forwarding / TCP reverse
     * proxy.
     *
     * If left to null, fall back to {@link #getPort()}
     *
     * @since 1.611
     */
    public static Integer CLI_PORT = SystemProperties.getInteger(TcpSlaveAgentListener.class.getName()+".port");
}

/*
Pasted from http://today.java.net/pub/a/today/2005/09/01/webstart.html

    Is it unrealistic to try to control access to JWS files?
    Is anyone doing this?

It is not unrealistic, and we are doing it. Create a protected web page
with a download button or link that makes a servlet call. If the user has
already logged in to your website, of course they can go there without
further authentication. The servlet reads the cookies sent by the browser
when the link is activated. It then generates a dynamic JNLP file adding
the authentication cookie and any other required cookies (JSESSIONID, etc.)
via <argument> tags. Write the WebStart application so that it picks up
any required cookies from the argument list, and adds these cookies to its
request headers on subsequent calls to the server. (Note: in the dynamic
JNLP file, do NOT put href= in the opening jnlp tag. If you do, JWS will
try to reload the JNLP from disk and since it's dynamic, it won't be there.
Leave it off and JWS will be happy.)

When returning the dynamic JNLP, the servlet should invoke setHeader(
"Expires", 0 ) and addDateHeader() twice on the servlet response to set
both "Date" and "Last-Modified" to the current date. This keeps the browser
from using a cached copy of a prior dynamic JNLP obtained from the same URL.

Note also that the JAR file(s) for the JWS application should not be on
a password-protected path - the launcher won't know about the authentication
cookie. But once the application starts, you can run all its requests
through a protected path requiring the authentication cookie, because
the application gets it from the dynamic JNLP. Just write it so that it
can't do anything useful without going through a protected path or doing
something to present credentials that could only have come from a valid
user.
*/
