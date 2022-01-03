package jenkins;

import static org.junit.Assert.assertNotNull;

import hudson.remoting.Channel;
import hudson.remoting.Which;
import java.io.File;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class RemotingJarSignatureTest {
    /**
     * Makes sure that the remoting jar is properly signed.
     */
    @Test
    public void testSignature() throws Exception {
        File jar = Which.jarFile(Channel.class);
        System.out.println("Verifying " + jar);

        JarFile myJar = new JarFile(jar, true);

        Enumeration<JarEntry> entries = myJar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory())    continue;

            // unsigned files that are related to signatures
            String name = entry.getName();
            if (name.equals("META-INF/MANIFEST.MF")) continue;
            if (name.startsWith("META-INF/") && name.endsWith(".SF")) continue;
            if (name.startsWith("META-INF/") && name.endsWith(".RSA")) continue;
            if (name.startsWith("META-INF/") && name.endsWith(".DSA")) continue;

            // make sure bits are signed
            IOUtils.copy(myJar.getInputStream(entry), NullOutputStream.NULL_OUTPUT_STREAM);
            assertNotNull("No signature for " + name, entry.getCodeSigners());
        }
    }
}
