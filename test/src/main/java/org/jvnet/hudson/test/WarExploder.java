/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt
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
package org.jvnet.hudson.test;

import hudson.remoting.Which;
import hudson.FilePath;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

/**
 * Ensures that <tt>jenkins.war</tt> is exploded.
 *
 * <p>
 * Depending on where the test is run (for example, inside Maven vs IDE), this code attempts to
 * use jenkins.war from the right place, thereby improving the productivity.
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
     * Explodes hudson.war, if necessary, and returns its root dir.
     */
    private static File explode() throws Exception {
        // are we in the Jenkins main workspace? If so, pick up hudson/main/war/resources
        // this saves the effort of packaging a war file and makes the debug cycle faster

        File d = new File(".").getAbsoluteFile();

        for( ; d!=null; d=d.getParentFile()) {
            if(new File(d,".jenkins").exists()) {
                File dir = new File(d,"war/target/jenkins");
                if(dir.exists()) {
                    System.out.println("Using jenkins.war resources from "+dir);
                    return dir;
                }
            }
        }

        // locate jenkins.war
        URL winstone = WarExploder.class.getResource("/winstone.jar");
        if(winstone==null)
            // impossible, since the test harness pulls in jenkins.war
            throw new AssertionError("jenkins.war is not in the classpath. If you are running this from the core workspace, run 'mvn install' to create the war image in war/target/jenkins");
        File war = Which.jarFile(Class.forName("executable.Executable"));

        // TODO this assumes that the CWD of the Maven process is the plugin ${basedir}, which may not be the case
        File buildDirectory = new File(System.getProperty("buildDirectory", "target"));
        File explodeDir = new File(buildDirectory, "jenkins-for-test").getAbsoluteFile();
        explodeDir.getParentFile().mkdirs();
        while (new File(explodeDir + ".exploding").isFile()) {
            explodeDir = new File(explodeDir + "x");
        }
        File timestamp = new File(explodeDir,".timestamp");

        if(!timestamp.exists() || (timestamp.lastModified()!=war.lastModified())) {
            System.out.println("Exploding " + war + " into " + explodeDir);
            new FileOutputStream(explodeDir + ".exploding").close();
            new FilePath(explodeDir).deleteRecursive();
            new FilePath(war).unzip(new FilePath(explodeDir));
            if(!explodeDir.exists())    // this is supposed to be impossible, but I'm investigating HUDSON-2605
                throw new IOException("Failed to explode "+war);
            new FileOutputStream(timestamp).close();
            timestamp.setLastModified(war.lastModified());
            new File(explodeDir + ".exploding").delete();
        } else {
            System.out.println("Picking up existing exploded jenkins.war at "+explodeDir.getAbsolutePath());
        }

        return explodeDir;
    }
}
