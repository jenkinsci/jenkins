package hudson.cli;

import static org.junit.Assert.*;

import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import org.codehaus.groovy.runtime.Security218;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConnectionTest {

    Throwable e;
    private Connection c1;
    private Connection c2;

    @Before
    public void setUp() throws IOException {
        FastPipedInputStream i = new FastPipedInputStream();
        FastPipedInputStream j = new FastPipedInputStream();

        c1 = new Connection(i,new FastPipedOutputStream(j));
        c2 = new Connection(j,new FastPipedOutputStream(i));
    }

    @Test
    public void testEncrypt() throws Throwable {
        final SecretKey sessionKey = new SecretKeySpec(new byte[16],"AES");

        Thread t1 = new Thread() {
            @Override
            public void run() {
                try {
                    c1.encryptConnection(sessionKey,"AES/CFB8/NoPadding").writeUTF("Hello");
                } catch (Throwable x) {
                    e = x;
                }
            }
        };
        t1.start();

        Thread t2 = new Thread() {
            @Override
            public void run() {
                try {
                    String data = c2.encryptConnection(sessionKey,"AES/CFB8/NoPadding").readUTF();
                    assertEquals("Hello", data);
                } catch (Throwable x) {
                    e = x;
                }
            }
        };
        t2.start();

        t1.join(9999);
        t2.join(9999);

        if (e != null) {
            throw e;
        }

        if (t1.isAlive() || t2.isAlive()) {
            t1.interrupt();
            t2.interrupt();
            throw new Error("thread is still alive");
        }
    }

    @Test
    public void testSecurity218() throws Exception {
        c1.writeObject(new Security218());
        try {
            c2.readObject();
            fail();
        } catch (SecurityException e) {
            assertTrue(e.getMessage().contains(Security218.class.getName()));
        }
    }
}
