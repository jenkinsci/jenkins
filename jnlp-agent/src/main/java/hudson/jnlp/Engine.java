package hudson.jnlp;

import hudson.remoting.Channel;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Kohsuke Kawaguchi
 */
public class Engine extends Thread {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private Listener listener;
    private final String host;
    private final String hudsonUrl;
    private final String secretKey;
    private final String slaveName;

    public Engine(Listener listener, String host, String hudsonUrl, String secretKey, String slaveName) {
        this.listener = listener;
        this.host = host;
        this.hudsonUrl = hudsonUrl;
        this.secretKey = secretKey;
        this.slaveName = slaveName;
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
        listener.status("Connecting");
        int retry = 1;
        while(true) {
            try {
                return new Socket(host, Integer.parseInt(port));
            } catch (IOException e) {
                if(retry++>10)
                    throw e;
                Thread.sleep(1000*10);
                listener.status("Connecting (retrying:"+retry+")");
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
}
