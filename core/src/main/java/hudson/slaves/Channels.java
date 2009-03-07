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
package hudson.slaves;

import hudson.remoting.Channel;
import hudson.Proc;

import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Various convenient subtype of {@link Channel}s.
 * 
 * @author Kohsuke Kawaguchi
 */
public class Channels {
    /**
     * Creates a channel that wraps a remote process, so that when we shut down the connection
     * we kill the process.
     */
    public static Channel forProcess(String name, ExecutorService execService, InputStream in, OutputStream out, final Proc proc) throws IOException {
        return new Channel(name, execService, in, out) {
            /**
             * Kill the process when the channel is severed.
             */
            protected synchronized void terminate(IOException e) {
                super.terminate(e);
                try {
                    proc.kill();
                } catch (IOException x) {
                    // we are already in the error recovery mode, so just record it and move on
                    LOGGER.log(Level.INFO, "Failed to terminate the severed connection",x);
                } catch (InterruptedException x) {
                    // process the interrupt later
                    Thread.currentThread().interrupt();
                }
            }

            public synchronized void close() throws IOException {
                super.close();
                // wait for Maven to complete
                try {
                    proc.join();
                } catch (InterruptedException e) {
                    // process the interrupt later
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(Channels.class.getName());
}
