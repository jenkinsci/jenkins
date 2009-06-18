/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jene Jasper, Stephen Connolly, Tom Huybrechts
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
package hudson.tasks;

import hudson.Extension;
import hudson.Launcher;
import hudson.Functions;
import hudson.EnvVars;
import hudson.Util;
import hudson.CopyOnWrite;
import hudson.Launcher.LocalLauncher;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.ArgumentListBuilder;
import hudson.util.NullStream;
import hudson.util.StreamTaskListener;
import hudson.util.VariableResolver;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.List;
import java.util.Collections;

/**
 * Build by using Maven.
 *
 * @author Kohsuke Kawaguchi
 */
public class Maven extends Builder {
    /**
     * The targets and other maven options.
     * Can be separated by SP or NL.
     */
    public final String targets;

    /**
     * Identifies {@link MavenInstallation} to be used.
     */
    public final String mavenName;

    /**
     * MAVEN_OPTS if not null.
     */
    public final String jvmOptions;

    /**
     * Optional POM file path relative to the workspace.
     * Used for the Maven '-f' option.
     */
    public final String pom;

    /**
     * Optional properties to be passed to Maven. Follows {@link Properties} syntax.
     */
    public final String properties;

    private final static String MAVEN_1_INSTALLATION_COMMON_FILE = "bin/maven";
    private final static String MAVEN_2_INSTALLATION_COMMON_FILE = "bin/mvn";

    public Maven(String targets,String name) {
        this(targets,name,null,null,null);
    }

    @DataBoundConstructor
    public Maven(String targets,String name, String pom, String properties, String jvmOptions) {
        this.targets = targets;
        this.mavenName = name;
        this.pom = Util.fixEmptyAndTrim(pom);
        this.properties = Util.fixEmptyAndTrim(properties);
        this.jvmOptions = Util.fixEmptyAndTrim(jvmOptions);
    }

    public String getTargets() {
        return targets;
    }

    /**
     * Gets the Maven to invoke,
     * or null to invoke the default one.
     */
    public MavenInstallation getMaven() {
        for( MavenInstallation i : getDescriptor().getInstallations() ) {
            if(mavenName !=null && i.getName().equals(mavenName))
                return i;
        }
        return null;
    }

    /**
     * Looks for <tt>pom.xlm</tt> or <tt>project.xml</tt> to determine the maven executable
     * name.
     */
    private static final class DecideDefaultMavenCommand implements FileCallable<String> {
        // command line arguments.
        private final String arguments;

        public DecideDefaultMavenCommand(String arguments) {
            this.arguments = arguments;
        }

        public String invoke(File ws, VirtualChannel channel) throws IOException {
            String seed=null;

            // check for the -f option
            StringTokenizer tokens = new StringTokenizer(arguments);
            while(tokens.hasMoreTokens()) {
                String t = tokens.nextToken();
                if(t.equals("-f") && tokens.hasMoreTokens()) {
                    File file = new File(ws,tokens.nextToken());
                    if(!file.exists())
                        continue;   // looks like an error, but let the execution fail later
                    if(file.isDirectory())
                        // in M1, you specify a directory in -f
                        seed = "maven";
                    else
                        // in M2, you specify a POM file name.
                        seed = "mvn";
                    break;
                }
            }

            if(seed==null) {
                // as of 1.212 (2008 April), I think Maven2 mostly replaced Maven1, so
                // switching to err on M2 side.
                if(new File(ws,"project.xml").exists())
                    seed = "maven";
                else
                    seed = "mvn";
            }

            if(Functions.isWindows())
                seed += ".bat";
            return seed;
        }
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        AbstractProject proj = build.getProject();

        VariableResolver<String> vr = build.getBuildVariableResolver();

        EnvVars env = build.getEnvironment(listener);

        String targets = Util.replaceMacro(this.targets,vr);
        targets = env.expand(targets);
        String pom = env.expand(this.pom);
        String jvmOptions = env.expand(this.jvmOptions);
        String properties =env.expand(this.properties);

        int startIndex = 0;
        int endIndex;
        do {
            // split targets into multiple invokations of maven separated by |
            endIndex = targets.indexOf('|', startIndex);
            if (-1 == endIndex) {
                endIndex = targets.length();
            }

            String normalizedTarget = targets
                    .substring(startIndex, endIndex)
                    .replaceAll("[\t\r\n]+"," ");

            ArgumentListBuilder args = new ArgumentListBuilder();
            MavenInstallation mi = getMaven();
            if(mi==null) {
                String execName = proj.getWorkspace().act(new DecideDefaultMavenCommand(normalizedTarget));
                args.add(execName);
            } else {
                mi = mi.forNode(Computer.currentComputer().getNode(), listener);
            	mi = mi.forEnvironment(env);
                String exec = mi.getExecutable(launcher);
                if(exec==null) {
                    listener.fatalError(Messages.Maven_NoExecutable(mi.getHome()));
                    return false;
                }
                args.add(exec);
            }
            if(pom!=null)
                args.add("-f",pom);
            args.addKeyValuePairs("-D",build.getBuildVariables());
            args.addKeyValuePairsFromPropertyString("-D",properties,vr);
            args.addTokenized(normalizedTarget);

            if(mi!=null) {
                // if somebody has use M2_HOME they will get a classloading error
                // when M2_HOME points to a different version of Maven2 from
                // MAVEN_HOME (as Maven 2 gives M2_HOME priority.)
                // 
                // The other solution would be to set M2_HOME if we are calling Maven2 
                // and MAVEN_HOME for Maven1 (only of use for strange people that
                // are calling Maven2 from Maven1)
                env.put("M2_HOME",mi.getHome());
                env.put("MAVEN_HOME",mi.getHome());
            }
            // just as a precaution
            // see http://maven.apache.org/continuum/faqs.html#how-does-continuum-detect-a-successful-build
            env.put("MAVEN_TERMINATE_CMD","on");

            if(jvmOptions!=null)
                env.put("MAVEN_OPTS",jvmOptions);

            try {
                int r = launcher.launch().cmds(args).envs(env).stdout(listener).pwd(proj.getModuleRoot()).join();
                if (0 != r) {
                    return false;
                }
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError(Messages.Maven_ExecFailed()) );
                return false;
            }
            startIndex = endIndex + 1;
        } while (startIndex < targets.length());
        return true;
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * @deprecated as of 1.286
     *      Use {@link Hudson#getDescriptorByType(Class)} to obtain the current instance.
     *      For compatibility, this field retains the last created {@link DescriptorImpl}.
     *      TODO: fix sonar plugin that depends on this. That's the only plugin that depends on this field.
     */
    public static DescriptorImpl DESCRIPTOR;

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @CopyOnWrite
        private volatile MavenInstallation[] installations = new MavenInstallation[0];

        public DescriptorImpl() {
            DESCRIPTOR = this;
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        protected void convert(Map<String, Object> oldPropertyBag) {
            if(oldPropertyBag.containsKey("installations"))
                installations = (MavenInstallation[]) oldPropertyBag.get("installations");
        }

        public String getHelpFile() {
            return "/help/project-config/maven.html";
        }

        public String getDisplayName() {
            return Messages.Maven_DisplayName();
        }

        public MavenInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(MavenInstallation... installations) {
            this.installations = installations;
            save();
        }

        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(Maven.class,formData);
        }
    }

    /**
     * Represents a Maven installation in a system.
     */
    public static final class MavenInstallation extends ToolInstallation implements EnvironmentSpecific<MavenInstallation>, NodeSpecific<MavenInstallation> {

        @Deprecated // kept for backward compatiblity - use getHome()
        private String mavenHome;

        /**
         * @deprecated as of 1.308.
         *      Use {@link #MavenInstallation(String, String, List)}
         */
        public MavenInstallation(String name, String home) {
            super(name, home);
        }

        @DataBoundConstructor
        public MavenInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
            super(name, home, properties);
        }

        /**
         * install directory.
         *
         * @deprecated as of 1.308. Use {@link #getHome()}.
         */
        public String getMavenHome() {
            return getHome();
        }

        public File getHomeDir() {
            return new File(getHome());
        }

        public String getHome() {
            if (mavenHome != null) return mavenHome;
            return super.getHome();
        }

        /**
         * Is this Maven 2.1.x?
         *
         * @param launcher
         *      Represents the node on which we evaluate the path.
         */
        public boolean isMaven2_1(Launcher launcher) throws IOException, InterruptedException {
            return launcher.getChannel().call(new Callable<Boolean,IOException>() {
                public Boolean call() throws IOException {
                    File[] jars = new File(getHomeDir(),"lib").listFiles();
                    if(jars!=null) // be defensive
                        for (File jar : jars)
                            if(jar.getName().startsWith("maven-2.1.") && jar.getName().endsWith("-uber.jar"))
                                return true;
                    return false;
                }
            });
        }

        /**
         * Gets the executable path of this maven on the given target system.
         */
        public String getExecutable(Launcher launcher) throws IOException, InterruptedException {
            return launcher.getChannel().call(new Callable<String,IOException>() {
                public String call() throws IOException {
                    File exe = getExeFile("maven");
                    if(exe.exists())
                        return exe.getPath();
                    exe = getExeFile("mvn");
                    if(exe.exists())
                        return exe.getPath();
                    return null;
                }
            });
        }

        private File getExeFile(String execName) {
            if(File.separatorChar=='\\')
                execName += ".bat";

            String m2Home = Util.replaceMacro(getHome(),EnvVars.masterEnvVars);

            return new File(m2Home, "bin/" + execName);
        }

        /**
         * Returns true if the executable exists.
         */
        public boolean getExists() {
            try {
                return getExecutable(new LocalLauncher(new StreamTaskListener(new NullStream())))!=null;
            } catch (IOException e) {
                return false;
            } catch (InterruptedException e) {
                return false;
            }
        }

        private static final long serialVersionUID = 1L;

		public MavenInstallation forEnvironment(EnvVars environment) {
			return new MavenInstallation(getName(), environment.expand(getHome()), getProperties().toList());
		}

        public MavenInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
            return new MavenInstallation(getName(), translateFor(node, log), getProperties().toList());
        }

        @Extension
        public static class DescriptorImpl extends ToolDescriptor<MavenInstallation> {
            @Override
            public String getDisplayName() {
                return "Maven";
            }

            @Override
            public List<? extends ToolInstaller> getDefaultInstallers() {
                return Collections.singletonList(new MavenInstaller(null));
            }

            @Override
            public MavenInstallation[] getInstallations() {
                return Hudson.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
            }

            @Override
            public void setInstallations(MavenInstallation... installations) {
                Hudson.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(installations);
            }

            @Override
            public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
                setInstallations(req.bindJSONToList(MavenInstallation.class, json.get("maven")).toArray(new MavenInstallation[0]));
                return true;
            }

            /**
             * Checks if the MAVEN_HOME is valid.
             */
            public FormValidation doCheckMavenHome(@QueryParameter File value) {
                // this can be used to check the existence of a file on the server, so needs to be protected
                if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                    return FormValidation.ok();

                if(value.getPath().equals(""))
                    return FormValidation.ok();

                if(!value.isDirectory())
                    return FormValidation.error(Messages.Maven_NotADirectory(value));

                File maven1File = new File(value,MAVEN_1_INSTALLATION_COMMON_FILE);
                File maven2File = new File(value,MAVEN_2_INSTALLATION_COMMON_FILE);

                if(!maven1File.exists() && !maven2File.exists())
                    return FormValidation.error(Messages.Maven_NotMavenDirectory(value));

                return FormValidation.ok();
            }
        }
    }

    /**
     * Automatic Maven installer from apache.org.
     */
    public static class MavenInstaller extends DownloadFromUrlInstaller {
        @DataBoundConstructor
        public MavenInstaller(String id) {
            super(id);
        }

        @Extension
        public static final class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<MavenInstaller> {
            public String getDisplayName() {
                return Messages.InstallFromApache();
            }

            @Override
            public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
                return toolType==MavenInstallation.class;
            }
        }
    }

    /**
     * Optional interface that can be implemented by {@link AbstractProject}
     * that has "contextual" {@link MavenInstallation} associated with it.
     *
     * <p>
     * Code like RedeployPublisher uses this interface in an attempt
     * to use the consistent Maven installation attached to the project.
     *
     * @since 1.235
     */
    public interface ProjectWithMaven {
        /**
         * Gets the {@link MavenInstallation} associated with the project.
         * Can be null.
         *
         * <p>
         * If the Maven installation can not be uniquely determined,
         * it's often better to return just one of them, rather than returning
         * null, since this method is currently ultimately only used to
         * decide where to parse <tt>conf/settings.xml</tt> from.
         */
        MavenInstallation inferMavenInstallation();
    }
}
