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

import hudson.model.Hudson;
import hudson.slaves.SlaveComputer;
import hudson.remoting.Channel;
import hudson.remoting.Channel.Listener;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens to incoming TCP connections from JNLP slave agents.
 *
 * <h2>Security</h2>
 * <p>
 * Once connected, remote slave agents can send in commands to be
 * executed on the master, so in a way this is like an rsh service.
 * Therefore, it is important that we reject connections from
 * unauthorized remote slaves.
 *
 * <p>
 * The approach here is to have {@link Hudson#getSecretKey() a secret key} on the master.
 * This key is sent to the slave inside the <tt>.jnlp</tt> file
 * (this file itself is protected by HTTP form-based authentication that
 * we use everywhere else in Hudson), and the slave sends this
 * token back when it connects to the master.
 * Unauthorized slaves can't access the protected <tt>.jnlp</tt> file,
 * so it can't impersonate a valid slave.
 *
 * <p>
 * We don't want to force the JNLP slave agents to be restarted
 * whenever the server restarts, so right now this secret master key
 * is generated once and used forever, which makes this whole scheme
 * less secure.
 *
 * @author Kohsuke Kawaguchi
 */
public final class TcpSlaveAgentListener extends Thread {

    private final ServerSocket serverSocket;
    private volatile boolean shuttingDown;

    public final int configuredPort;

    /**
     * @param port
     *      Use 0 to choose a random port.
     */
    public TcpSlaveAgentListener(int port) throws IOException {
        super("TCP slave agent listener port="+port);
        serverSocket = new ServerSocket(port);
        this.configuredPort = port;

        LOGGER.info("JNLP slave agent listener started on TCP port "+getPort());

        start();
    }

    /**
     * Gets the TCP port number in which we are listening.
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    private String getSecretKey() {
        return Hudson.getInstance().getSecretKey();
    }

    public void run() {
        try {
            // the loop eventually terminates when the socket is closed.
            while (true) {
                Socket s = serverSocket.accept();
                new ConnectionHandler(s).start();
            }
        } catch (IOException e) {
            if(!shuttingDown) {
                LOGGER.log(Level.SEVERE,"Failed to accept JNLP slave agent connections",e);
            }
        }
    }

    /**
     * Initiates the shuts down of the listener.
     */
    public void shutdown() {
        shuttingDown = true;
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
        }

        public void run() {
            try {
                LOGGER.info("Accepted connection #"+id+" from "+s.getRemoteSocketAddress());

                DataInputStream in = new DataInputStream(s.getInputStream());
                PrintWriter out = new PrintWriter(s.getOutputStream(),true);

                String s = in.readUTF();

                if(s.startsWith("Protocol:")) {
                    String protocol = s.substring(9);
                    if(protocol.equals("JNLP-connect")) {
                        runJnlpConnect(in, out);
                    } else {
                        error(out, "Unknown protocol:" + s);
                    }
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

        /**
         * Handles JNLP slave agent connection request.
         */
        private void runJnlpConnect(DataInputStream in, PrintWriter out) throws IOException, InterruptedException {
            if(!getSecretKey().equals(in.readUTF())) {
                error(out, "Unauthorized access");
                return;
            }

            String nodeName = in.readUTF();
            SlaveComputer computer = (SlaveComputer) Hudson.getInstance().getComputer(nodeName);
            if(computer==null) {
                error(out, "No such slave: "+nodeName);
                return;
            }

            if(computer.getChannel()!=null) {
                error(out, nodeName+" is already connected to this master. Rejecting this connection.");
                return;
            }

            out.println("Welcome");

            final OutputStream log = computer.openLogFile();
            new PrintWriter(log).println("JNLP agent connected from "+ this.s.getInetAddress());

            computer.setChannel(new BufferedInputStream(this.s.getInputStream()), new BufferedOutputStream(this.s.getOutputStream()), log,
                new Listener() {
                    public void onClosed(Channel channel, IOException cause) {
                        try {
                            log.close();
                        } catch (IOException e) {
                            e.printStackTrace(); 
                        }
                        if(cause!=null)
                            LOGGER.log(Level.WARNING, "Connection #"+id+" terminated",cause);
                        try {
                            ConnectionHandler.this.s.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                });
        }

        private void error(PrintWriter out, String msg) throws IOException {
            out.println(msg);
            LOGGER.log(Level.WARNING,"Connection #"+id+" is aborted: "+msg);
            s.close();
        }
    }

    private static int iotaGen=1;

    private static final Logger LOGGER = Logger.getLogger(TcpSlaveAgentListener.class.getName());
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