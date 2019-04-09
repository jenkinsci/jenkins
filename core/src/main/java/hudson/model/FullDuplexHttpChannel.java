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

import hudson.remoting.Channel;
import hudson.remoting.PingThread;
import hudson.remoting.Channel.Mode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.FullDuplexHttpService;

/**
 * Builds a {@link Channel} on top of two HTTP streams (one used for each direction.)
 *
 * @author Kohsuke Kawaguchi
 * @deprecated Unused.
 */
@Deprecated
abstract public class FullDuplexHttpChannel extends FullDuplexHttpService {
    private Channel channel;
    private final boolean restricted;

    public FullDuplexHttpChannel(UUID uuid, boolean restricted) throws IOException {
        super(uuid);
        this.restricted = restricted;
    }

    @Override
    protected void run(final InputStream upload, OutputStream download) throws IOException, InterruptedException {
        channel = new Channel("HTTP full-duplex channel " + uuid,
                Computer.threadPoolForRemoting, Mode.BINARY, upload, download, null, restricted);

        // so that we can detect dead clients, periodically send something
        PingThread ping = new PingThread(channel) {
            @Override
            protected void onDead(Throwable diagnosis) {
                LOGGER.log(Level.INFO, "Duplex-HTTP session " + uuid + " is terminated", diagnosis);
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
    }

    protected abstract void main(Channel channel) throws IOException, InterruptedException;

    public Channel getChannel() {
        return channel;
    }

    private static final Logger LOGGER = Logger.getLogger(FullDuplexHttpChannel.class.getName());

}
