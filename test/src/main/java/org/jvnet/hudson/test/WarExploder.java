package org.jvnet.hudson.test;

import hudson.remoting.Which;
import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.net.URL;

/**
 * Ensures that <tt>hudson.war</tt> is exploded.
 *
 * @author Kohsuke Kawaguchi
 */
final class WarExploder {
    public static final File EXPLODE_DIR = explode();

    /**
     * Explodes a war, if necesasry, and returns its root dir.
     */
    private static File explode() {
        try {
            // locate hudson.war
            URL winstone = WarExploder.class.getResource("/winstone.jar");
            if(winstone==null)
            // impossible, since the test harness pulls in hudson.war
                throw new AssertionError("hudson.war is not in the classpath.");
            File war = Which.jarFile(Class.forName("executable.Executable"));

            File explodeDir = new File("./target/hudson-for-test");
            File timestamp = new File(explodeDir,".timestamp");

            if(!timestamp.exists() || (timestamp.lastModified()==war.lastModified())) {
                System.out.println("Picking up hudson.war at "+war);
                new FilePath(explodeDir).deleteRecursive();
                new FilePath(war).unzip(new FilePath(explodeDir));
                new FileOutputStream(timestamp).close();
                timestamp.setLastModified(war.lastModified());
            } else {
                System.out.println("Picking up existing exploded hudson.war");
            }

            return explodeDir;
        } catch (IOException e) {
            throw new Error(e);
        } catch (InterruptedException e) {
            throw new Error(e);
        } catch (ClassNotFoundException e) {
            throw new Error(e);
        }
    }
}
