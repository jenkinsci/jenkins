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
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.List;
import java.util.Collections;

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

    public void setNoReconnect(boolean noReconnect) {
        this.noReconnect = noReconnect;
    }

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
                    try {
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

                Socket s = connect(port);

                listener.status("Handshaking");
                DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                dos.writeUTF("Protocol:JNLP-connect");
                dos.writeUTF(secretKey);
                dos.writeUTF(slaveName);

                Channel channel = new Channel("channel", executor,
                        new BufferedInputStream(s.getInputStream()),
                        new BufferedOutputStream(s.getOutputStream()));
                listener.status("Connected");
                channel.join();
                listener.status("Terminated");
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
                return new Socket(host, Integer.parseInt(port));
            } catch (IOException e) {
                if(retry++>10)
                    throw e;
                Thread.sleep(1000*10);
                listener.status(msg+" (retrying:"+retry+")");
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
                HttpURLConnection con = (HttpURLConnection)hudsonUrl.openConnection();
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
}
