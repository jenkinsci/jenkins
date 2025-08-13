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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

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
    private static final long serialVersionUID = -3318291200160313357L;

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
        super(name, javaHome, Collections.emptyList());
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
        return new File(getHome(), "bin");
    }
    /**
     * Gets the path to 'java'.
     */

    private File getExecutable() {
        String execName = File.separatorChar == '\\' ? "java.exe" : "java";
        return new File(getHome(), "bin/" + execName);
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
    public void buildEnvVars(Map<String, String> env) {
        String home = getHome();
        if (home == null) {
            return;
        }
        // see EnvVars javadoc for why this adds PATH.
        env.put("PATH+JDK", home + "/bin");
        env.put("JAVA_HOME", home);
    }

    /**
     * Sets PATH and JAVA_HOME from this JDK.
     */
    @Override
    public void buildEnvVars(EnvVars env) {
        buildEnvVars((Map) env);
    }

    @Override
    public JDK forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new JDK(getName(), translateFor(node, log));
    }

    @Override
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
            TaskListener listener = new StreamTaskListener(OutputStream.nullOutputStream());
            Launcher launcher = n.createLauncher(listener);
            return launcher.launch().cmds("java", "-fullversion").stdout(listener).join() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Extension @Symbol("jdk")
    public static class DescriptorImpl extends ToolDescriptor<JDK> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.JDK_DisplayName();
        }

        @Override
        public JDK[] getInstallations() {
            return Jenkins.get().getJDKs().toArray(new JDK[0]);
        }

        @Override
        public void setInstallations(JDK... jdks) {
            Jenkins.get().setJDKs(Arrays.asList(jdks));
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            try {
                Class<? extends ToolInstaller> jdkInstallerClass = Jenkins.get().getPluginManager()
                        .uberClassLoader.loadClass("hudson.tools.JDKInstaller").asSubclass(ToolInstaller.class);
                Constructor<? extends ToolInstaller> constructor = jdkInstallerClass.getConstructor(String.class, boolean.class);
                return List.of(constructor.newInstance(null, false));
            } catch (ClassNotFoundException e) {
                return Collections.emptyList();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Unable to get default installer", e);
                return Collections.emptyList();
            }
        }

        /**
         * Checks if the JAVA_HOME is a valid JAVA_HOME path.
         */
        @Override protected FormValidation checkHomeDirectory(File value) {
            File toolsJar = new File(value, "lib/tools.jar");
            File mac = new File(value, "lib/dt.jar");

            // JENKINS-25601: JDK 9+ no longer has tools.jar. Keep the existing dt.jar/tools.jar checks to be safe.
            File javac = new File(value, "bin/javac");
            File javacExe = new File(value, "bin/javac.exe");
            if (!toolsJar.exists() && !mac.exists() && !javac.exists() && !javacExe.exists())
                return FormValidation.error(Messages.Hudson_NotJDKDir(value));

            return FormValidation.ok();
        }

    }

    public static class ConverterImpl extends ToolConverter {
        public ConverterImpl(XStream2 xstream) { super(xstream); }

        @Override protected String oldHomeField(ToolInstallation obj) {
            return ((JDK) obj).javaHome;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JDK.class.getName());
}
