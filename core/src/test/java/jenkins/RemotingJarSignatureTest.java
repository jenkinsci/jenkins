package jenkins;

import static org.junit.Assert.fail;

import hudson.remoting.Channel;
import hudson.remoting.Which;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.Test;

import java.io.File;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.commons.io.IOUtils;

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
//        File jar = new File("/home/kohsuke/.m2/repository/org/jenkins-ci/main/remoting/1.421/remoting-1.421.jar");
//        File jar = new File("/home/kohsuke/.m2/repository/org/jenkins-ci/main/remoting/2.0/remoting-2.0.jar");
        System.out.println("Verifying "+jar);

        JarFile myJar = new JarFile(jar,true);

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
            IOUtils.copy(myJar.getInputStream(entry), new NullOutputStream());
            if (entry.getCodeSigners()==null) {
                fail("No signature for " + name);
            }
        }
    }
}
