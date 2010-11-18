/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.remoting.forward;

import hudson.remoting.VirtualChannel;
import hudson.remoting.Callable;
import hudson.remoting.SocketInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.SocketOutputStream;
import hudson.remoting.Channel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Creates {@link Forwarder}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ForwarderFactory {
    /**
     * Creates a connector on the remote side that connects to the speicied host and port.
     */
    public static Forwarder create(VirtualChannel channel, final String remoteHost, final int remotePort) throws IOException, InterruptedException {
        return channel.call(new Callable<Forwarder,IOException>() {
            public Forwarder call() throws IOException {
                return new ForwarderImpl(remoteHost,remotePort);
            }

            private static final long serialVersionUID = 1L;
        });
    }

    public static Forwarder create(String remoteHost, int remotePort) {
        return new ForwarderImpl(remoteHost,remotePort);
    }

    private static class ForwarderImpl implements Forwarder {
        private final String remoteHost;
        private final int remotePort;

        private ForwarderImpl(String remoteHost, int remotePort) {
            this.remoteHost = remoteHost;
            this.remotePort = remotePort;
        }

        public OutputStream connect(OutputStream out) throws IOException {
            Socket s = new Socket(remoteHost, remotePort);
            new CopyThread(String.format("Copier to %s:%d", remoteHost, remotePort),
                new SocketInputStream(s), out).start();
            return new RemoteOutputStream(new SocketOutputStream(s));
        }

        /**
         * When sent to the remote node, send a proxy.
         */
        private Object writeReplace() {
            return Channel.current().export(Forwarder.class, this);
        }
    }
}
