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

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.model.Hudson;
import jenkins.model.Jenkins;
import hudson.util.OneShotEvent;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors a UDP multicast broadcast and respond with the location of the Hudson service.
 *
 * <p>
 * Useful for auto-discovery of Hudson in the network.
 *
 * @author Kohsuke Kawaguchi
 */
public class UDPBroadcastThread extends Thread {
    private final Jenkins jenkins;

    public final OneShotEvent ready = new OneShotEvent();
    private MulticastSocket mcs;
    private boolean shutdown;
    static boolean udpHandlingProblem; // for tests

    /**
     * @deprecated as of 1.416
     *      Use {@link #UDPBroadcastThread(Jenkins)}
     */
    @Deprecated
    public UDPBroadcastThread(Hudson jenkins) throws IOException {
        this((Jenkins)jenkins);
    }

    public UDPBroadcastThread(Jenkins jenkins) throws IOException {
        super("Jenkins UDP "+PORT+" monitoring thread");
        this.jenkins = jenkins;
        mcs = new MulticastSocket(PORT);
    }

    @SuppressWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    @Override
    public void run() {
        try {
            mcs.joinGroup(MULTICAST);
            ready.signal();

            while(true) {
                byte[] buf = new byte[2048];
                DatagramPacket p = new DatagramPacket(buf,buf.length);
                mcs.receive(p);

                SocketAddress sender = p.getSocketAddress();

                // prepare a response
                TcpSlaveAgentListener tal = jenkins.getTcpSlaveAgentListener();

                StringBuilder rsp = new StringBuilder("<hudson>");
                tag(rsp,"version", Jenkins.VERSION);
                tag(rsp,"url", jenkins.getRootUrl());
                tag(rsp,"server-id", jenkins.getLegacyInstanceId());
                tag(rsp,"slave-port",tal==null?null:tal.getPort());

                for (UDPBroadcastFragment f : UDPBroadcastFragment.all())
                    f.buildFragment(rsp,sender);

                rsp.append("</hudson>");

                byte[] response = rsp.toString().getBytes("UTF-8");
                mcs.send(new DatagramPacket(response,response.length,sender));
            }
        } catch (ClosedByInterruptException e) {
            // shut down
        } catch (BindException e) {
            // if we failed to listen to UDP, just silently abandon it, as a stack trace
            // makes people unnecessarily concerned, for a feature that currently does no good.
            LOGGER.log(Level.WARNING, "Failed to listen to UDP port "+PORT,e);
        } catch (IOException e) {
            if (shutdown)   return; // forcibly closed
            LOGGER.log(Level.WARNING, "UDP handling problem",e);
            udpHandlingProblem = true;
        }
    }

    private void tag(StringBuilder buf, String tag, Object value) {
        if(value==null) return;
        buf.append('<').append(tag).append('>').append(value).append("</").append(tag).append('>');
    }

    public void shutdown() {
        shutdown = true;
        mcs.close();
        interrupt();
    }

    public static final int PORT = Integer.getInteger("hudson.udp",33848);

    private static final Logger LOGGER = Logger.getLogger(UDPBroadcastThread.class.getName());

    /**
     * Multicast socket address.
     */
    public static InetAddress MULTICAST;

    static {
        try {
            MULTICAST = InetAddress.getByAddress(new byte[]{(byte)239, (byte)77, (byte)124, (byte)213});
        } catch (UnknownHostException e) {
            throw new Error(e);
        }
    }
}
