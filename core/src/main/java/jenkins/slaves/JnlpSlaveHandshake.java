package jenkins.slaves;

import hudson.model.Computer;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.Engine;
import org.jenkinsci.remoting.nio.NioChannelHub;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Palette of objects to talk to the incoming JNLP agent connection.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.561
 */
public class JnlpSlaveHandshake {
    /**
     * Useful for creating a {@link Channel} with NIO as the underlying transport.
     */
    /*package*/  final NioChannelHub hub;

    /**
     * Socket connection to the agent.
     */
    /*package*/  final Socket socket;

    /**
     * Wrapping Socket input stream.
     */
    /*package*/ final DataInputStream in;

    /**
     * For writing handshaking response.
     *
     * This is a poor design choice that we just carry forward for compatibility.
     * For better protocol design, {@link DataOutputStream} is preferred for newer
     * protocols.
     */
    /*package*/  final PrintWriter out;

    /**
     * Bag of properties the JNLP agent have sent us during the hand-shake.
     */
    /*package*/ final Properties request = new Properties();


    /*package*/ JnlpSlaveHandshake(NioChannelHub hub, Socket socket, DataInputStream in, PrintWriter out) {
        this.hub = hub;
        this.socket = socket;
        this.in = in;
        this.out = out;
    }

    public NioChannelHub getHub() {
        return hub;
    }

    public Socket getSocket() {
        return socket;
    }

    public DataInputStream getIn() {
        return in;
    }

    public PrintWriter getOut() {
        return out;
    }

    public Properties getRequestProperties() {
        return request;
    }

    public String getRequestProperty(String name) {
        return request.getProperty(name);
    }


    /**
     * Sends the error output and bail out.
     */
    public void error(String msg) throws IOException {
        out.println(msg);
        LOGGER.log(Level.WARNING,Thread.currentThread().getName()+" is aborted: "+msg);
        socket.close();
    }

    /**
     * {@link JnlpAgentReceiver} calls this method to tell the client that the server
     * is happy with the handshaking and is ready to move on to build a channel.
     */
    public void success(Properties response) {
        out.println(Engine.GREETING_SUCCESS);
        for (Entry<Object, Object> e : response.entrySet()) {
            out.println(e.getKey()+": "+e.getValue());
        }
        out.println(); // empty line to conclude the response header
    }

    public ChannelBuilder createChannelBuilder(String nodeName) {
        if (hub==null)
            return new ChannelBuilder(nodeName, Computer.threadPoolForRemoting);
        else
            return hub.newChannelBuilder(nodeName, Computer.threadPoolForRemoting);
    }


    private static final Logger LOGGER = Logger.getLogger(JnlpSlaveHandshake.class.getName());
}
