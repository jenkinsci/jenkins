package hudson.jnlp;

import hudson.remoting.Channel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.Socket;
import java.io.IOException;
import java.io.DataOutputStream;

/**
 * @author Kohsuke Kawaguchi
 */
public class Engine extends Thread {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private Listener listener;
    private final String host;
    private final int port;
    private final String secretKey;
    private final String slaveName;

    public Engine(Listener listener, String host, int port, String secretKey, String slaveName) {
        this.listener = listener;
        this.host = host;
        this.port = port;
        this.secretKey = secretKey;
        this.slaveName = slaveName;
    }

    public void run() {
        try {
            listener.status("Connecting");
            Socket s = new Socket(host, port);
            listener.status("Handshaking");

            DataOutputStream dos = new DataOutputStream(s.getOutputStream());
            dos.writeUTF(secretKey);
            dos.writeUTF(slaveName);

            Channel channel = new Channel("channel", executor, s.getInputStream(), s.getOutputStream());
            listener.status("Connected");
            channel.join();
            listener.status("Terminated");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
