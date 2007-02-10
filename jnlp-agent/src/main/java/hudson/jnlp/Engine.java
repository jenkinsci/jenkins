package hudson.jnlp;

import hudson.remoting.Channel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.Socket;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class Engine extends Thread {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private Listener listener;
    private String host;
    private int port;

    public Engine(Listener listener, String host, int port) {
        this.listener = listener;
        this.host = host;
        this.port = port;
    }

    public void run() {
        try {
            listener.status("Connecting");
            Socket s = new Socket(host, port);
            listener.status("Handshaking");

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
