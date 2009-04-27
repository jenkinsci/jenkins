package hudson.model;

import hudson.remoting.Channel;
import hudson.remoting.PingThread;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Builds a {@link Channel} on top of two HTTP streams (one used for each direction.)
 *
 * @author Kohsuke Kawaguchi
 */
final class FullDuplexHttpChannel {
    private Channel channel;

    private final PipedOutputStream pipe = new PipedOutputStream();

    private final UUID uuid;

    public FullDuplexHttpChannel(UUID uuid) throws IOException {
        this.uuid = uuid;
    }

    /**
     * This is where we send the data to the client.
     *
     * <p>
     * If this connection is lost, we'll abort the channel.
     */
    public void download(StaplerRequest req, StaplerResponse rsp) throws InterruptedException, IOException {
        rsp.setStatus(HttpServletResponse.SC_OK);

        // server->client channel.
        // this is created first, and this controls the lifespan of the channel
        rsp.addHeader("Transfer-Encoding", "chunked");
        channel = new Channel("HTTP full-duplex channel " + uuid,
                Computer.threadPoolForRemoting, new PipedInputStream(pipe), rsp.getOutputStream());

        // so that we can detect dead clients, periodically send something
        PingThread ping = new PingThread(channel) {
            @Override
            protected void onDead() {
                LOGGER.info("Duplex-HTTP session " + uuid + " is terminated");
                // this will cause the channel to abort and subsequently clean up
                try {
                    pipe.close();
                } catch (IOException e) {
                    // this can never happen
                    throw new AssertionError(e);
                }
            }
        };
        ping.start();
        channel.join();
        ping.interrupt();
    }

    /**
     * This is where we receive inputs from the client.
     */
    public void upload(StaplerRequest req, StaplerResponse rsp) throws InterruptedException, IOException {
        rsp.setStatus(HttpServletResponse.SC_OK);
        IOUtils.copy(req.getInputStream(),pipe);
    }

    public Channel getChannel() {
        return channel;
    }

    private static final Logger LOGGER = Logger.getLogger(FullDuplexHttpChannel.class.getName());
}
