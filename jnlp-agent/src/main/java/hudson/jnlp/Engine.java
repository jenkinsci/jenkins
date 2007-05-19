package hudson.jnlp;

import hudson.remoting.Channel;

import java.io.DataOutputStream;
import java.io.IOException;
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
                if(con.getResponseCode()!=200
                || port ==null) {
                    listener.error(new Exception(hudsonUrl+" is not Hudson: "+con.getResponseMessage()));
                    return;
                }

                listener.status("Connecting");
                Socket s = new Socket(host, Integer.parseInt(port));
                listener.status("Handshaking");

                DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                dos.writeUTF("Protocol:JNLP-connect");
                dos.writeUTF(secretKey);
                dos.writeUTF(slaveName);

                Channel channel = new Channel("channel", executor, s.getInputStream(), s.getOutputStream());
                listener.status("Connected");
                channel.join();
                listener.status("Terminated");

                // try to connect back to the server every 10 secs.
                while(true) {
                    Thread.sleep(1000*10);
                    try {
                        con = (HttpURLConnection)new URL(hudsonUrl).openConnection();
                        con.connect();
                        if(con.getResponseCode()!=200)
                        continue;
                    } catch (IOException e) {
                        continue;
                    }
                    break;
                }
            }
        } catch (Throwable e) {
            listener.error(e);
        }
    }
}
