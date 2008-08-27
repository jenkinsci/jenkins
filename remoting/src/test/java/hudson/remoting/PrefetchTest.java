package hudson.remoting;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.attrs.StackMapAttribute;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class PrefetchTest extends RmiTestBase {
    public void testPrefetch() throws Exception {
        VerifyTask vt = new VerifyTask();
        assertTrue( channel.preloadJar(vt,ClassReader.class));
        assertFalse(channel.preloadJar(vt,ClassReader.class));
        // TODO: how can I do a meaningful test of this feature?
        System.out.println(channel.call(vt));
    }

    private static class VerifyTask implements Callable<String,IOException> {
        public String call() throws IOException {
            StackMapAttribute sma = new StackMapAttribute();
            return Which.jarFile(sma.getClass()).getPath();
        }
    }
}
