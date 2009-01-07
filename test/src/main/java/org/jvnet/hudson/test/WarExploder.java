package org.jvnet.hudson.test;

import hudson.remoting.Which;
import hudson.FilePath;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

/**
 * Ensures that <tt>hudson.war</tt> is exploded.
 *
 * @author Kohsuke Kawaguchi
 */
final class WarExploder {

    public static File getExplodedDir() throws Exception {
        // rethrow an exception every time someone tries to do this, so that when explode()
        // fails, you can see the cause no matter which test case you look at.
        // see http://www.nabble.com/Failing-tests-in-test-harness-module-on-hudson.ramfelt.se-td19258722.html
        if(FAILURE !=null)   throw new Exception("Failed to initialize exploded war", FAILURE);
        return EXPLODE_DIR;
    }

    private static File EXPLODE_DIR;
    private static Exception FAILURE;

    static {
        try {
            EXPLODE_DIR = explode();
        } catch (Exception e) {
            FAILURE = e;
        }
    }

    /**
     * Explodes hudson.war, if necesasry, and returns its root dir.
     */
    private static File explode() throws Exception {
        // are we in the hudson main workspace? If so, pick up hudson/main/war/resources
        // this saves the effort of packaging a war file and makes the debug cycle faster
        File d = new File(".").getAbsoluteFile();
        for( ; d!=null; d=d.getParentFile()) {
            if(!d.getName().equals("main")) continue;
            if(new File(d,".hudson").exists())
                break;
        }
        if(d!=null) {
            File dir = new File(d,"war/target/hudson");
            if(dir.exists()) {
                System.out.println("Using hudson.war resources from "+dir);
                return dir;
            }
        }

        // locate hudson.war
        URL winstone = WarExploder.class.getResource("/winstone.jar");
        if(winstone==null)
        // impossible, since the test harness pulls in hudson.war
            throw new AssertionError("hudson.war is not in the classpath.");
        File war = Which.jarFile(Class.forName("executable.Executable"));

        File explodeDir = new File("./target/hudson-for-test").getAbsoluteFile();
        File timestamp = new File(explodeDir,".timestamp");

        if(!timestamp.exists() || (timestamp.lastModified()!=war.lastModified())) {
            System.out.println("Exploding hudson.war at "+war);
            new FilePath(explodeDir).deleteRecursive();
            new FilePath(war).unzip(new FilePath(explodeDir));
            if(!explodeDir.exists())    // this is supposed to be impossible, but I'm investigating HUDSON-2605
                throw new IOException("Failed to explode "+war);
            new FileOutputStream(timestamp).close();
            timestamp.setLastModified(war.lastModified());
        } else {
            System.out.println("Picking up existing exploded hudson.war at "+explodeDir.getAbsolutePath());
        }

        return explodeDir;
    }
}
