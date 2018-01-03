/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import hudson.EnvVars;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.Collections;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Information about JDK installation.
 *
 * @author Kohsuke Kawaguchi
 */
public final class JDK extends ToolInstallation implements NodeSpecific<JDK>, EnvironmentSpecific<JDK> {

    /**
     * Name of the “System JDK”, which is just the JDK on Jenkins' $PATH.
     * @since 1.577
     */
    public static final String DEFAULT_NAME = "(System)";

    @Restricted(NoExternalUse.class)
    public static boolean isDefaultName(String name) {
        if ("(Default)".equals(name)) {
            // DEFAULT_NAME took this value prior to 1.598.
            return true;
        }
        return DEFAULT_NAME.equals(name) || name == null;
    }

    /**
     * @deprecated since 2009-02-25
     */
    @Deprecated // kept for backward compatibility - use getHome() instead
    private transient String javaHome;

    public JDK(String name, String javaHome) {
        super(name, javaHome, Collections.<ToolProperty<?>>emptyList());
    }

    @DataBoundConstructor
    public JDK(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    /**
     * install directory.
     *
     * @deprecated as of 1.304
     *      Use {@link #getHome()}
     */
    @Deprecated
    public String getJavaHome() {
        return getHome();
    }

    /**
     * Gets the path to the bin directory.
     */
    public File getBinDir() {
        return new File(getHome(),"bin");
    }
    /**
     * Gets the path to 'java'.
     */
    private File getExecutable() {
        String execName = (File.separatorChar == '\\') ? "java.exe" : "java";
        return new File(getHome(),"bin/"+execName);
    }

    /**
     * Returns true if the executable exists.
     */
    public boolean getExists() {
        return getExecutable().exists();
    }

    /**
     * @deprecated as of 1.460. Use {@link #buildEnvVars(EnvVars)}
     */
    @Deprecated
    public void buildEnvVars(Map<String,String> env) {
        String home = getHome();
        if (home == null) {
            return;
        }
        // see EnvVars javadoc for why this adds PATH.
        env.put("PATH+JDK",home+"/bin");
        env.put("JAVA_HOME", home);
    }

    /**
     * Sets PATH and JAVA_HOME from this JDK.
     */
    @Override
    public void buildEnvVars(EnvVars env) {
        buildEnvVars((Map)env);
    }

    public JDK forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new JDK(getName(), translateFor(node, log));
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
            return launcher.launch().cmds("java","-fullversion").stdout(listener).join()==0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            return false;
        }
    }

    public static class ConverterImpl extends ToolConverter {
        public ConverterImpl(XStream2 xstream) { super(xstream); }
        @Override protected String oldHomeField(ToolInstallation obj) {
            return ((JDK)obj).javaHome;
        }
    }
}
