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

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.init.Terminator;
import hudson.model.Hudson;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import hudson.util.OneShotEvent;

import static java.util.logging.Level.SEVERE;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;

import com.google.inject.Injector;

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

    private static UDPBroadcastThread udpBroadcastThread;
    
    @Initializer(after=InitMilestone.PLUGINS_PREPARED)
    @Restricted(NoExternalUse.class)
    public static synchronized void startUdpBroadcastThread() {
        if(udpBroadcastThread == null && UDPBroadcastThread.PORT != -1) {
            try {
                // check config for this feature being enabled
                Jenkins jenkins = Jenkins.getInstance();
                jenkins.checkPermission(Jenkins.ADMINISTER);
                Injector injector = jenkins.getInjector();
                UDPBroadcastThreadGlobalConfiguration config = injector.getInstance(UDPBroadcastThreadGlobalConfiguration.class);
                if (config.isUdpBroadcastEnabled()) {
                    udpBroadcastThread = new UDPBroadcastThread(Jenkins.getInstance());
                    udpBroadcastThread.start();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to broadcast over UDP (use -Dhudson.udp=-1 to disable)", e);
            }
        }
    }
    
    @Terminator
    @Restricted(NoExternalUse.class)
    public static synchronized void stopUdpBroadcastThread() throws Exception {
        Jenkins jenkins = Jenkins.getInstance();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        if(udpBroadcastThread!=null) {
            LOGGER.log(Level.FINE, "Shutting down {0}", udpBroadcastThread.getName());
            try {
                udpBroadcastThread.shutdown();
                udpBroadcastThread = null;
            } catch (Exception e) {
                LOGGER.log(SEVERE, "Failed to shutdown UDP Broadcast Thread", e);
                // save for later
                throw e;
            }
        }
    }

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
        } catch (SocketException e) {
            if (shutdown) { // forcibly closed
                return;
            }            // if we failed to listen to UDP, just silently abandon it, as a stack trace
            // makes people unnecessarily concerned, for a feature that currently does no good.
            LOGGER.log(Level.INFO, "Cannot listen to UDP port {0}, skipping: {1}", new Object[] {PORT, e});
            LOGGER.log(Level.FINE, null, e);
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
    
    @Extension(ordinal=193) // after DNSMultiCast
    public static class UDPBroadcastThreadGlobalConfiguration extends GlobalConfiguration {
        public UDPBroadcastThreadGlobalConfiguration() {
            load();
        }
        
        // JENKINS-33596 - disable UDP by default
        private boolean isUdpBroadcastEnabled = false;
        
        @Override
        public GlobalConfigurationCategory getCategory() {
            return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
        }
        
        public boolean isUdpBroadcastEnabled() {
            return isUdpBroadcastEnabled;
        }
        
        public void setUdpBroadcastEnabled(boolean isUdpBroadcastEnabled) {
            this.isUdpBroadcastEnabled = isUdpBroadcastEnabled;
        }
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            // for compatibility reasons, the actual value is stored in Jenkins
            if (json.getBoolean("udpBroadcastEnabled")) {
                isUdpBroadcastEnabled = true;
                startUdpBroadcastThread();;
            } else {
                isUdpBroadcastEnabled = false;
                try {
                    stopUdpBroadcastThread();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Unable to stop UDP Broadcast Services: ", e);
                }
            }
            save();
            return true;
        }
    }
}
