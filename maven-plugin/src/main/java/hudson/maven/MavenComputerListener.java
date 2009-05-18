/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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
package hudson.maven;

import hudson.Extension;
import hudson.FilePath;
import hudson.maven.agent.Main;
import hudson.maven.agent.PluginManagerInterceptor;
import hudson.maven.agent.Maven21Interceptor;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.slaves.ComputerListener;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.Project;
import sun.tools.jar.resources.jar;

/**
 * When a slave is connected, copy <tt>maven-agent.jar</tt> and <tt>maven-intercepter.jar</tt>
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class MavenComputerListener extends ComputerListener {
    @Override
    public void preOnline(Computer c, Channel channel,FilePath root,  TaskListener listener) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        copyJar(logger, root, Main.class, "maven-agent");
        copyJar(logger, root, PluginManagerInterceptor.class, "maven-interceptor");
        copyJar(logger, root, Maven21Interceptor.class, "maven2.1-interceptor");
    }

    /**
     * Copies a jar file from the master to slave.
     */
    private void copyJar(PrintStream log, FilePath dst, Class<?> representative, String seedName) throws IOException, InterruptedException {
        // in normal execution environment, the master should be loading 'representative' from this jar, so
        // in that way we can find it.
        File jar = Which.jarFile(representative);

        if(jar.isDirectory()) {
            // but during the development and unit test environment, we may be picking the class up from the classes dir
            Zip zip = new Zip();
            zip.setBasedir(jar);
            File t = File.createTempFile(seedName, "jar");
            t.delete();
            zip.setDestFile(t);
            zip.setProject(new Project());
            zip.execute();
            jar = t;
        }

        new FilePath(jar).copyTo(dst.child(seedName +".jar"));
        log.println("Copied "+seedName+".jar");
    }
}
