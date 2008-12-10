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
     * Host name of Hudson master to connect to, when opening a TCP connection.
     */
    public final String host;
    /**
     * URL that points to Hudson's tcp slage agent listener, like <tt>http://myhost/hudson/tcpSlaveAgentListener/</tt>
     */
    public final String hudsonUrl;
    private final String secretKey;
    public final String slaveName;

    /**
     * See Main#tunnel in the jnlp-agent module for the details.
     */
    private String tunnel;

    public Engine(EngineListener listener, String host, String hudsonUrl, String secretKey, String slaveName) {
        this.listener = listener;
        this.host = host;
        this.hudsonUrl = hudsonUrl;
        this.secretKey = secretKey;
        this.slaveName = slaveName;
    }

    public void setTunnel(String tunnel) {
        this.tunnel = tunnel;
    }

    public void run() {
        try {
            while(true) {
                listener.status("Locating Server");
                // find out the TCP port
                HttpURLConnection con = (HttpURLConnection)new URL(hudsonUrl).openConnection();
                con.connect();
                String port = con.getHeaderField("X-Hudson-JNLP-Port");
                if(con.getResponseCode()!=200) {
                    listener.error(new Exception(hudsonUrl+" is invalid: "+con.getResponseCode()+" "+con.getResponseMessage()));
                    return;
                }
                if(port ==null) {
                    listener.error(new Exception(hudsonUrl+" is not Hudson: "));
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
        String host = this.host;

        if(tunnel!=null) {
            String[] tokens = tunnel.split(":");
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
                HttpURLConnection con = (HttpURLConnection)new URL(hudsonUrl).openConnection();
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
