/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.model;

import hudson.SystemProperties;
import hudson.remoting.Channel;
import hudson.remoting.PingThread;
import hudson.remoting.Channel.Mode;
import hudson.util.ChunkedOutputStream;
import hudson.util.ChunkedInputStream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds a {@link Channel} on top of two HTTP streams (one used for each direction.)
 *
 * @author Kohsuke Kawaguchi
 */
abstract public class FullDuplexHttpChannel {
    private Channel channel;

    private InputStream upload;

    private final UUID uuid;
    private final boolean restricted;

    private boolean completed;

    public FullDuplexHttpChannel(UUID uuid, boolean restricted) throws IOException {
        this.uuid = uuid;
        this.restricted = restricted;
    }

    /**
     * This is where we send the data to the client.
     *
     * <p>
     * If this connection is lost, we'll abort the channel.
     */
    public synchronized void download(StaplerRequest req, StaplerResponse rsp) throws InterruptedException, IOException {
        rsp.setStatus(HttpServletResponse.SC_OK);

        // server->client channel.
        // this is created first, and this controls the lifespan of the channel
        rsp.addHeader("Transfer-Encoding", "chunked");
        OutputStream out = rsp.getOutputStream();
        if (DIY_CHUNKING) out = new ChunkedOutputStream(out);

        // send something out so that the client will see the HTTP headers
        out.write("Starting HTTP duplex channel".getBytes());
        out.flush();

        {// wait until we have the other channel
            long end = System.currentTimeMillis() + CONNECTION_TIMEOUT;
            while (upload == null && System.currentTimeMillis()<end)
                wait(1000);

            if (upload==null)
                throw new IOException("HTTP full-duplex channel timeout: "+uuid);
        }

        try {
            channel = new Channel("HTTP full-duplex channel " + uuid,
                    Computer.threadPoolForRemoting, Mode.BINARY, upload, out, null, restricted);

            // so that we can detect dead clients, periodically send something
            PingThread ping = new PingThread(channel) {
                @Override
                protected void onDead(Throwable diagnosis) {
                    LOGGER.log(Level.INFO,"Duplex-HTTP session " + uuid + " is terminated",diagnosis);
                    // this will cause the channel to abort and subsequently clean up
                    try {
                        upload.close();
                    } catch (IOException e) {
                        // this can never happen
                        throw new AssertionError(e);
                    }
                }

                @Override
                protected void onDead() {
                    onDead(null);
                }
            };
            ping.start();
            main(channel);
            channel.join();
            ping.interrupt();
        } finally {
            // publish that we are done
            completed=true;
            notify();
        }
    }

    protected abstract void main(Channel channel) throws IOException, InterruptedException;

    /**
     * This is where we receive inputs from the client.
     */
    public synchronized void upload(StaplerRequest req, StaplerResponse rsp) throws InterruptedException, IOException {
        rsp.setStatus(HttpServletResponse.SC_OK);
        InputStream in = req.getInputStream();
        if(DIY_CHUNKING)    in = new ChunkedInputStream(in);

        // publish the upload channel
        upload = in;
        notify();

        // wait until we are done
        while (!completed)
            wait();
    }

    public Channel getChannel() {
        return channel;
    }

    private static final Logger LOGGER = Logger.getLogger(FullDuplexHttpChannel.class.getName());

    /**
     * Set to true if the servlet container doesn't support chunked encoding.
     */
    @Restricted(NoExternalUse.class)
    public static boolean DIY_CHUNKING = SystemProperties.getBoolean("hudson.diyChunking");

    /**
     * Controls the time out of waiting for the 2nd HTTP request to arrive.
     */
    @Restricted(NoExternalUse.class)
    public static long CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(15);
}
