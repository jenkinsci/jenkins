/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import hudson.util.StreamTaskListener;
import hudson.util.NullStream;
import hudson.Launcher;
import hudson.Extension;
import hudson.EnvVars;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolLocationNodeProperty;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.List;

/**
 * Information about JDK installation.
 *
 * @author Kohsuke Kawaguchi
 */
public final class JDK extends ToolInstallation implements NodeSpecific<JDK>, EnvironmentSpecific<JDK> {
    @Deprecated // kept for backward compatibility - use getHome() instead
    private String javaHome;

    public JDK(String name, String javaHome) {
        super(name, javaHome);
    }

    /**
     * install directory.
     */
    public String getJavaHome() {
        return getHome();
    }

    public String getHome() {
        if (javaHome != null) return javaHome;
        return super.getHome();
    }

    /**
     * Gets the path to the bin directory.
     */
    public File getBinDir() {
        return new File(getJavaHome(),"bin");
    }
    /**
     * Gets the path to 'java'.
     */
    private File getExecutable() {
        String execName;
        if(File.separatorChar=='\\')
            execName = "java.exe";
        else
            execName = "java";

        return new File(getJavaHome(),"bin/"+execName);
    }

    /**
     * Returns true if the executable exists.
     */
    public boolean getExists() {
        return getExecutable().exists();
    }

    /**
     * Sets PATH and JAVA_HOME from this JDK.
     */
    public void buildEnvVars(Map<String,String> env) {
        // see EnvVars javadoc for why this adss PATH.
        env.put("PATH+JDK",getBinDir().getPath());
        env.put("JAVA_HOME",getJavaHome());
    }

    public JDK forNode(Node node) {
        return new JDK(getName(),translateFor(node));
    }

    public JDK forEnvironment(EnvVars environment) {
        return new JDK(getName(), environment.expand(getHome()));
    }

    /**
     * Checks if "java" is in PATH on the given node.
     *
     * <p>
     * If it's not, then the user must specify a configured JDK,
     * so this is often useful for form field validation.
     */
    public static boolean isDefaultJDKValid(Node n) {
        try {
            TaskListener listener = new StreamTaskListener(new NullStream());
            Launcher launcher = n.createLauncher(listener);
            return launcher.launch("java -fullversion",new String[0],listener.getLogger(),null).join()==0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            return false;
        }
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<JDK> {

        public String getDisplayName() {
            return "Java Development Kit";
        }

        public JDK[] getInstallations() {
            return Hudson.getInstance().getJDKs().toArray(new JDK[0]);
        }

        // this isn't really synchronized well since the list is Hudson.jdks :(
        public synchronized void setInstallations(JDK... jdks) {
            List<JDK> list = Hudson.getInstance().getJDKs();
            list.clear();
            for (JDK jdk: jdks) list.add(jdk);
        }

    }
}
