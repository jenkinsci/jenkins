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
package hudson;

import hudson.model.Hudson;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.BindException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors a UDP broadcast and respond with the location of the Hudson service.
 *
 * <p>
 * Useful for auto-discovery of Hudson in the network.
 *
 * @author Kohsuke Kawaguchi
 */
public class UDPBroadcastThread extends Thread {
    private final Hudson hudson;
    public UDPBroadcastThread(Hudson hudson) {
        super("Hudson UDP "+PORT+" monitoring thread");
        this.hudson = hudson;
    }

    @Override
    public void run() {
        try {
            DatagramChannel ch = DatagramChannel.open();
            try {
                ch.socket().bind(new InetSocketAddress(PORT));

                ByteBuffer b = ByteBuffer.allocate(2048);
                while(true) {
                    // the only thing that matters here is who sent it, not what was sent.
                    SocketAddress sender = ch.receive(b);

                    // prepare a response
                    TcpSlaveAgentListener tal = hudson.getTcpSlaveAgentListener();

                    StringBuilder buf = new StringBuilder("<hudson>");
                    tag(buf,"version",Hudson.VERSION);
                    tag(buf,"url",hudson.getRootUrl());
                    tag(buf,"slave-port",tal==null?null:tal.getPort());

                    for (UDPBroadcastFragment f : UDPBroadcastFragment.all())
                        f.buildFragment(buf,sender);

                    buf.append("</hudson>");

                    b.clear();
                    b.put(buf.toString().getBytes("UTF-8"));
                    b.flip();
                    ch.send(b, sender);
                }
            } finally {
                ch.close();
            }
        } catch (ClosedByInterruptException e) {
            // shut down
        } catch (BindException e) {
            // if we failed to listen to UDP, just silently abandon it, as a stack trace
            // makes people unnecessarily concerned, for a feature that currently does no good.
            LOGGER.log(Level.FINE, "Failed to listen to UDP port "+PORT,e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "UDP handling problem",e);
        }
    }

    private void tag(StringBuilder buf, String tag, Object value) {
        if(value==null) return;
        buf.append('<').append(tag).append('>').append(value).append("</").append(tag).append('>');
    }

    public void shutdown() {
        interrupt();
    }

    public static final int PORT = Integer.getInteger("hudson.udp",33848);

    private static final Logger LOGGER = Logger.getLogger(UDPBroadcastThread.class.getName());
}
