/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.remoting;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.List;
import java.util.Collections;
import java.util.logging.Logger;
import static java.util.logging.Level.SEVERE;

/**
 * Slave agent engine that proactively connects to Hudson master.
 *
 * @author Kohsuke Kawaguchi
 */
public class Engine extends Thread {
    /**
     * Thread pool that sets {@link #CURRENT}.
     */
    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
        public Thread newThread(final Runnable r) {
            return defaultFactory.newThread(new Runnable() {
                public void run() {
                    CURRENT.set(Engine.this);
                    r.run();
                }
            });
        }
    });

    public final EngineListener listener;

    /**
     * To make Hudson more graceful against user error,
     * JNLP agent can try to connect to multiple possible Hudson URLs.
     * This field specifies those candidate URLs, such as
     * "http://foo.bar/hudson/".
     */
    private List<URL> candidateUrls;

    /**
     * URL that points to Hudson's tcp slage agent listener, like <tt>http://myhost/hudson/</tt>
     *
     * <p>
     * This value is determined from {@link #candidateUrls} after a successful connection.
     * Note that this URL <b>DOES NOT</b> have "tcpSlaveAgentListener" in it.
     */
    private URL hudsonUrl;

    private final String secretKey;
    public final String slaveName;
    private String credentials;

    /**
     * See Main#tunnel in the jnlp-agent module for the details.
     */
    private String tunnel;

    private boolean noReconnect;

    public Engine(EngineListener listener, List<URL> hudsonUrls, String secretKey, String slaveName) {
        this.listener = listener;
        this.candidateUrls = hudsonUrls;
        this.secretKey = secretKey;
        this.slaveName = slaveName;
        if(candidateUrls.isEmpty())
            throw new IllegalArgumentException("No URLs given");
    }

    public URL getHudsonUrl() {
        return hudsonUrl;
    }

    public void setTunnel(String tunnel) {
        this.tunnel = tunnel;
    }

    public void setCredentials(String creds) {
        this.credentials = creds;
    }

    public void setNoReconnect(boolean noReconnect) {
        this.noReconnect = noReconnect;
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Override
    public void run() {
        try {
            boolean first = true;
            while(true) {
                if(first) {
                    first = false;
                } else {
                    if(noReconnect)
                        return; // exit
                }

                listener.status("Locating server among " + candidateUrls);
                Throwable firstError=null;
                String port=null;

                for (URL url : candidateUrls) {
                    String s = url.toExternalForm();
                    if(!s.endsWith("/"))    s+='/';
                    URL salURL = new URL(s+"tcpSlaveAgentListener/");

                    // find out the TCP port
                    HttpURLConnection con = (HttpURLConnection)salURL.openConnection();
                    if (con instanceof HttpURLConnection && credentials != null) {
                        String encoding = new sun.misc.BASE64Encoder().encode(credentials.getBytes());
                        con.setRequestProperty("Authorization", "Basic " + encoding);
                    }
                    try {
                        try {
                            con.setConnectTimeout(30000);
                            con.setReadTimeout(60000);
                            con.connect();
                        } catch (IOException x) {
                            if (firstError == null) {
                                firstError = new IOException("Failed to connect to " + salURL + ": " + x.getMessage()).initCause(x);
                            }
                            continue;
                        }
                        port = con.getHeaderField("X-Hudson-JNLP-Port");
                        if(con.getResponseCode()!=200) {
                            if(firstError==null)
                                firstError = new Exception(salURL+" is invalid: "+con.getResponseCode()+" "+con.getResponseMessage());
                            continue;
                        }
                        if(port ==null) {
                            if(firstError==null)
                                firstError = new Exception(url+" is not Hudson");
                            continue;
                        }
                    } finally {
                        con.disconnect();
                    }

                    // this URL works. From now on, only try this URL
                    hudsonUrl = url;
                    firstError = null;
                    candidateUrls = Collections.singletonList(hudsonUrl);
                    break;
                }

                if(firstError!=null) {
                    listener.error(firstError);
                    return;
                }

                final Socket s = connect(port);

                listener.status("Handshaking");
                DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                dos.writeUTF("Protocol:JNLP-connect");
                dos.writeUTF(secretKey);
                dos.writeUTF(slaveName);

                BufferedInputStream in = new BufferedInputStream(s.getInputStream());
                String greeting = readLine(in); // why, oh why didn't I use DataOutputStream when writing to the network?
                if (!greeting.equals(GREETING_SUCCESS)) {
                    listener.error(new Exception("The server rejected the connection: "+greeting));
                    Thread.sleep(10*1000);
                    continue;
                }

                final Channel channel = new Channel("channel", executor,
                        in,
                        new BufferedOutputStream(s.getOutputStream()));
                PingThread t = new PingThread(channel) {
                    protected void onDead() {
                        try {
                            if (!channel.isInClosed()) {
                                LOGGER.info("Ping failed. Terminating the socket.");
                                s.close();
                            }
                        } catch (IOException e) {
                            LOGGER.log(SEVERE, "Failed to terminate the socket", e);
                        }
                    }
                };
                t.start();
                listener.status("Connected");
                channel.join();
                listener.status("Terminated");
                t.interrupt();  // make sure the ping thread is terminated
                listener.onDisconnect();

                if(noReconnect)
                    return; // exit
                // try to connect back to the server every 10 secs.
                waitForServerToBack();
            }
        } catch (Throwable e) {
            listener.error(e);
        }
    }

    /**
     * Read until '\n' and returns it as a string.
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            int ch = in.read();
            if (ch<0 || ch=='\n')
                return baos.toString().trim(); // trim off possible '\r'
            baos.write(ch);
        }
    }

    /**
     * Connects to TCP slave port, with a few retries.
     */
    private Socket connect(String port) throws IOException, InterruptedException {
        String host = this.hudsonUrl.getHost();

        if(tunnel!=null) {
            String[] tokens = tunnel.split(":",3);
            if(tokens.length!=2)
                throw new IOException("Illegal tunneling parameter: "+tunnel);
            if(tokens[0].length()>0)    host = tokens[0];
            if(tokens[1].length()>0)    port = tokens[1];
        }

        String msg = "Connecting to " + host + ':' + port;
        listener.status(msg);
        int retry = 1;
        while(true) {
            try {
                Socket s = new Socket(host, Integer.parseInt(port));
                s.setTcpNoDelay(true); // we'll do buffering by ourselves

                // set read time out to avoid infinite hang. the time out should be long enough so as not
                // to interfere with normal operation. the main purpose of this is that when the other peer dies
                // abruptly, we shouldn't hang forever, and at some point we should notice that the connection
                // is gone.
                s.setSoTimeout(30*60*1000); // 30 mins. See PingThread for the ping interval
                return s;
            } catch (IOException e) {
                if(retry++>10)
                    throw e;
                Thread.sleep(1000*10);
                listener.status(msg+" (retrying:"+retry+")",e);
            }
        }
    }

    /**
     * Waits for the server to come back.
     */
    private void waitForServerToBack() throws InterruptedException {
        while(true) {
            Thread.sleep(1000*10);
            try {
                // Hudson top page might be read-protected. see http://www.nabble.com/more-lenient-retry-logic-in-Engine.waitForServerToBack-td24703172.html
                HttpURLConnection con = (HttpURLConnection)new URL(hudsonUrl,"tcpSlaveAgentListener/").openConnection();
                con.connect();
                if(con.getResponseCode()==200)
                    return;
            } catch (IOException e) {
                // retry
            }
        }
    }

    /**
     * When invoked from within remoted {@link Callable} (that is,
     * from the thread that carries out the remote requests),
     * this method returns the {@link Engine} in which the remote operations
     * run.
     */
    public static Engine current() {
        return CURRENT.get();
    }

    private static final ThreadLocal<Engine> CURRENT = new ThreadLocal<Engine>();

    private static final Logger LOGGER = Logger.getLogger(Engine.class.getName());

    public static final String GREETING_SUCCESS = "Welcome";
}
