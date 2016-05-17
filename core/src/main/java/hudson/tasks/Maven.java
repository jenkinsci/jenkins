/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jene Jasper, Stephen Connolly, Tom Huybrechts, Yahoo! Inc.
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
import jenkins.MasterToSlaveFileCallable;
import hudson.Launcher;
import hudson.Functions;
import hudson.EnvVars;
import hudson.Util;
import hudson.CopyOnWrite;
import hudson.Launcher.LocalLauncher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import jenkins.model.Jenkins;
import jenkins.mvn.GlobalMavenConfig;
import jenkins.mvn.GlobalSettingsProvider;
import jenkins.mvn.SettingsProvider;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeSpecific;
import hudson.tasks._maven.MavenConsoleAnnotator;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.ArgumentListBuilder;
import hudson.util.NullStream;
import hudson.util.StreamTaskListener;
import hudson.util.VariableResolver;
import hudson.util.VariableResolver.ByMap;
import hudson.util.VariableResolver.Union;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;

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

    /**
     * If true, the build will use its own local Maven repository
     * via "-Dmaven.repo.local=...".
     * <p>
     * This would consume additional disk space, but provides isolation with other builds on the same machine,
     * such as mixing SNAPSHOTS. Maven also doesn't try to coordinate the concurrent access to Maven repositories
     * from multiple Maven process, so this helps there too.
     *
     * Identical to logic used in maven-plugin.
     *
     * @since 1.322
     */
    public boolean usePrivateRepository = false;
    
    /**
     * Provides access to the settings.xml to be used for a build.
     * @since 1.491
     */
    private SettingsProvider settings;
    
    /**
     * Provides access to the global settings.xml to be used for a build.
     * @since 1.491
     */
    private GlobalSettingsProvider globalSettings;

    /**
     * Skip injecting build variables as properties into maven process.
     *
     * Defaults to false to mimic the legacy behavior.
     *
     * @since TODO
     */
    private boolean doNotInjectBuildVariables = false;

    private final static String MAVEN_1_INSTALLATION_COMMON_FILE = "bin/maven";
    private final static String MAVEN_2_INSTALLATION_COMMON_FILE = "bin/mvn";
    
    private static final Pattern S_PATTERN = Pattern.compile("(^| )-s ");
    private static final Pattern GS_PATTERN = Pattern.compile("(^| )-gs ");

    public Maven(String targets,String name) {
        this(targets,name,null,null,null,false, null, null);
    }

    public Maven(String targets, String name, String pom, String properties, String jvmOptions) {
        this(targets, name, pom, properties, jvmOptions, false, null, null);
    }
    
    public Maven(String targets,String name, String pom, String properties, String jvmOptions, boolean usePrivateRepository) {
        this(targets, name, pom, properties, jvmOptions, usePrivateRepository, null, null);
    }
    
    public Maven(String targets,String name, String pom, String properties, String jvmOptions, boolean usePrivateRepository, SettingsProvider settings, GlobalSettingsProvider globalSettings) {
        this(targets, name, pom, properties, jvmOptions, usePrivateRepository, settings, globalSettings, true);
    }

    @DataBoundConstructor
    public Maven(String targets,String name, String pom, String properties, String jvmOptions, boolean usePrivateRepository, SettingsProvider settings, GlobalSettingsProvider globalSettings, boolean injectBuildVariables) {
        this.targets = targets;
        this.mavenName = name;
        this.pom = Util.fixEmptyAndTrim(pom);
        this.properties = Util.fixEmptyAndTrim(properties);
        this.jvmOptions = Util.fixEmptyAndTrim(jvmOptions);
        this.usePrivateRepository = usePrivateRepository;
        this.settings = settings != null ? settings : GlobalMavenConfig.get().getSettingsProvider();
        this.globalSettings = globalSettings != null ? globalSettings : GlobalMavenConfig.get().getGlobalSettingsProvider();
        this.doNotInjectBuildVariables = !injectBuildVariables;
    }

    public String getTargets() {
        return targets;
    }

    /**
     * @since 1.491
     */
    public SettingsProvider getSettings() {
        return settings != null ? settings : GlobalMavenConfig.get().getSettingsProvider();
    }
    
    protected void setSettings(SettingsProvider settings) {
        this.settings = settings;
    }
    
    /**
     * @since 1.491
     */
    public GlobalSettingsProvider getGlobalSettings() {
        return globalSettings != null ? globalSettings : GlobalMavenConfig.get().getGlobalSettingsProvider();
    }
    
    protected void setGlobalSettings(GlobalSettingsProvider globalSettings) {
        this.globalSettings = globalSettings;
    }

    public void setUsePrivateRepository(boolean usePrivateRepository) {
        this.usePrivateRepository = usePrivateRepository;
    }

    public boolean usesPrivateRepository() {
        return usePrivateRepository;
    }

    @Restricted(NoExternalUse.class) // Exposed for view
    public boolean isInjectBuildVariables() {
        return !doNotInjectBuildVariables;
    }

    /**
     * Gets the Maven to invoke,
     * or null to invoke the default one.
     */
    public MavenInstallation getMaven() {
        for( MavenInstallation i : getDescriptor().getInstallations() ) {
            if(mavenName !=null && mavenName.equals(i.getName()))
                return i;
        }
        return null;
    }

    /**
     * Looks for <tt>pom.xlm</tt> or <tt>project.xml</tt> to determine the maven executable
     * name.
     */
    private static final class DecideDefaultMavenCommand extends MasterToSlaveFileCallable<String> {
        private static final long serialVersionUID = -2327576423452215146L;
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
                    seed = file.isDirectory() ?
                        /* in M1, you specify a directory in -f */ "maven"
                        /* in M2, you specify a POM file name.  */ : "mvn";
                    break;
                }
            }

            if(seed==null) {
                // as of 1.212 (2008 April), I think Maven2 mostly replaced Maven1, so
                // switching to err on M2 side.
                seed = new File(ws,"project.xml").exists() ? "maven" : "mvn";
            }

            return seed;
        }
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        VariableResolver<String> vr = build.getBuildVariableResolver();

        EnvVars env = build.getEnvironment(listener);

        String targets = Util.replaceMacro(this.targets,vr);
        targets = env.expand(targets);
        String pom = env.expand(this.pom);

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
                String execName = build.getWorkspace().act(new DecideDefaultMavenCommand(normalizedTarget));
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
            
            
            if(!S_PATTERN.matcher(targets).find()){ // check the given target/goals do not contain settings parameter already
                String settingsPath = SettingsProvider.getSettingsRemotePath(getSettings(), build, listener);
                if(StringUtils.isNotBlank(settingsPath)){
                    args.add("-s", settingsPath);
                }
            }
            if(!GS_PATTERN.matcher(targets).find()){
                String settingsPath = GlobalSettingsProvider.getSettingsRemotePath(getGlobalSettings(), build, listener);
                if(StringUtils.isNotBlank(settingsPath)){
                    args.add("-gs", settingsPath);
                }
            }

            if (isInjectBuildVariables()) {
                Set<String> sensitiveVars = build.getSensitiveBuildVariables();
                args.addKeyValuePairs("-D",build.getBuildVariables(),sensitiveVars);
                final VariableResolver<String> resolver = new Union<String>(new ByMap<String>(env), vr);
                args.addKeyValuePairsFromPropertyString("-D",this.properties,resolver,sensitiveVars);
            }

            if (usesPrivateRepository())
                args.add("-Dmaven.repo.local=" + build.getWorkspace().child(".repository"));
            args.addTokenized(normalizedTarget);
            wrapUpArguments(args,normalizedTarget,build,launcher,listener);

            buildEnvVars(env, mi);
            
            if (!launcher.isUnix()) {
                args = args.toWindowsCommand();
            }

            try {
                MavenConsoleAnnotator mca = new MavenConsoleAnnotator(listener.getLogger(),build.getCharset());
                int r = launcher.launch().cmds(args).envs(env).stdout(mca).pwd(build.getModuleRoot()).join();
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

    /**
     * Allows the derived type to make additional modifications to the arguments list.
     *
     * @since 1.344
     */
    protected void wrapUpArguments(ArgumentListBuilder args, String normalizedTarget, AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    }

    /**
     * Build up the environment variables toward the Maven launch.
     */
    protected void buildEnvVars(EnvVars env, MavenInstallation mi) throws IOException, InterruptedException {
        if(mi!=null) {
            // if somebody has use M2_HOME they will get a classloading error
            // when M2_HOME points to a different version of Maven2 from
            // MAVEN_HOME (as Maven 2 gives M2_HOME priority.)
            //
            // The other solution would be to set M2_HOME if we are calling Maven2
            // and MAVEN_HOME for Maven1 (only of use for strange people that
            // are calling Maven2 from Maven1)
            mi.buildEnvVars(env);
        }
        // just as a precaution
        // see http://maven.apache.org/continuum/faqs.html#how-does-continuum-detect-a-successful-build
        env.put("MAVEN_TERMINATE_CMD","on");

        String jvmOptions = env.expand(this.jvmOptions);
        if(jvmOptions!=null)
            env.put("MAVEN_OPTS",jvmOptions.replaceAll("[\t\r\n]+"," "));
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * @deprecated as of 1.286
     *      Use {@link jenkins.model.Jenkins#getDescriptorByType(Class)} to obtain the current instance.
     *      For compatibility, this field retains the last created {@link DescriptorImpl}.
     *      TODO: fix sonar plugin that depends on this. That's the only plugin that depends on this field.
     */
    @Deprecated
    public static DescriptorImpl DESCRIPTOR;

    @Extension @Symbol("maven")
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

        @Override
        public String getHelpFile(String fieldName) {
            if (fieldName != null && fieldName.equals("globalSettings")) fieldName = "settings"; // same help file
            return super.getHelpFile(fieldName);
        }

        public String getDisplayName() {
            return Messages.Maven_DisplayName();
        }
        
        public GlobalSettingsProvider getDefaultGlobalSettingsProvider() {
            return GlobalMavenConfig.get().getGlobalSettingsProvider();
        }
        
        public SettingsProvider getDefaultSettingsProvider() {
            return GlobalMavenConfig.get().getSettingsProvider();
        }

        public MavenInstallation[] getInstallations() {
            return installations;
        }

		public void setInstallations(MavenInstallation... installations) {
			List<MavenInstallation> tmpList = new ArrayList<Maven.MavenInstallation>();
			// remote empty Maven installation : 
			if(installations != null) {
				Collections.addAll(tmpList, installations);
				for(MavenInstallation installation : installations) {
					if(Util.fixEmptyAndTrim(installation.getName()) == null) {
						tmpList.remove(installation);
					}
				}
			}
            this.installations = tmpList.toArray(new MavenInstallation[tmpList.size()]);
            save();
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(Maven.class,formData);
        }
    }

    /**
     * Represents a Maven installation in a system.
     */
    public static final class MavenInstallation extends ToolInstallation implements EnvironmentSpecific<MavenInstallation>, NodeSpecific<MavenInstallation> {
        /**
         * Constants for describing Maven versions for comparison.
         */
        public static final int MAVEN_20 = 0;
        public static final int MAVEN_21 = 1;
        public static final int MAVEN_30 = 2;
        
    
        /**
         * @deprecated since 2009-02-25.
         */
        @Deprecated // kept for backward compatibility - use getHome()
        private transient String mavenHome;

        /**
         * @deprecated as of 1.308.
         *      Use {@link #Maven.MavenInstallation(String, String, List)}
         */
        @Deprecated
        public MavenInstallation(String name, String home) {
            super(name, home);
        }

        @DataBoundConstructor
        public MavenInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
            super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home), properties);
        }

        /**
         * install directory.
         *
         * @deprecated as of 1.308. Use {@link #getHome()}.
         */
        @Deprecated
        public String getMavenHome() {
            return getHome();
        }

        public File getHomeDir() {
            return new File(getHome());
        }

        @Override
        public void buildEnvVars(EnvVars env) {
            String home = getHome();
            if (home == null) {
                return;
            }
            env.put("M2_HOME", home);
            env.put("MAVEN_HOME", home);
            env.put("PATH+MAVEN", home + "/bin");
        }

        /**
         * Compares the version of this Maven installation to the minimum required version specified.
         *
         * @param launcher
         *      Represents the node on which we evaluate the path.
         * @param mavenReqVersion
         *      Represents the minimum required Maven version - constants defined above.
         */
        public boolean meetsMavenReqVersion(Launcher launcher, int mavenReqVersion) throws IOException, InterruptedException {
            // FIXME using similar stuff as in the maven plugin could be better 
            // olamy : but will add a dependency on maven in core -> so not so good 
            String mavenVersion = launcher.getChannel().call(new MasterToSlaveCallable<String,IOException>() {
                    private static final long serialVersionUID = -4143159957567745621L;

                    public String call() throws IOException {
                        File[] jars = new File(getHomeDir(),"lib").listFiles();
                        if(jars!=null) { // be defensive
                            for (File jar : jars) {
                                if (jar.getName().startsWith("maven-")) {
                                    JarFile jf = null;
                                    try {
                                        jf = new JarFile(jar);
                                        Manifest manifest = jf.getManifest();
                                        String version = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                                        if(version != null) return version;
                                    } finally {
                                        if(jf != null) jf.close();
                                    }
                                }
                            }
                        }
                        return "";
                    }
                });

            if (!mavenVersion.equals("")) {
                if (mavenReqVersion == MAVEN_20) {
                    if(mavenVersion.startsWith("2."))
                        return true;
                }
                else if (mavenReqVersion == MAVEN_21) {
                    if(mavenVersion.startsWith("2.") && !mavenVersion.startsWith("2.0"))
                        return true;
                }
                else if (mavenReqVersion == MAVEN_30) {
                    if(mavenVersion.startsWith("3."))
                        return true;
                }                
            }
            return false;
            
        }
        
        /**
         * Is this Maven 2.1.x or 2.2.x - but not Maven 3.x?
         *
         * @param launcher
         *      Represents the node on which we evaluate the path.
         */
        public boolean isMaven2_1(Launcher launcher) throws IOException, InterruptedException {
            return meetsMavenReqVersion(launcher, MAVEN_21);
        }

        /**
         * Gets the executable path of this maven on the given target system.
         */
        public String getExecutable(Launcher launcher) throws IOException, InterruptedException {
            return launcher.getChannel().call(new MasterToSlaveCallable<String,IOException>() {
                private static final long serialVersionUID = 2373163112639943768L;

                public String call() throws IOException {
                    File exe = getExeFile("mvn");
                    if(exe.exists())
                        return exe.getPath();
                    exe = getExeFile("maven");
                    if(exe.exists())
                        return exe.getPath();
                    return null;
                }
            });
        }

        private File getExeFile(String execName) {
            String m2Home = Util.replaceMacro(getHome(),EnvVars.masterEnvVars);

            if(Functions.isWindows()) {
                File exeFile = new File(m2Home, "bin/" + execName + ".bat");

                // since Maven 3.3 .bat files are replaced with .cmd
                if (!exeFile.exists()) {
                    return new File(m2Home, "bin/" + execName + ".cmd");
                }

                return exeFile;
            } else {
                return new File(m2Home, "bin/" + execName);
            }
        }

        /**
         * Returns true if the executable exists.
         */
        public boolean getExists() {
            try {
                return getExecutable(new LocalLauncher(new StreamTaskListener(new NullStream())))!=null;
            } catch (IOException | InterruptedException e) {
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

        @Extension @Symbol("maven")
        public static class DescriptorImpl extends ToolDescriptor<MavenInstallation> {
            @Override
            public String getDisplayName() {
                return "Maven";
            }

            @Override
            public List<? extends ToolInstaller> getDefaultInstallers() {
                return Collections.singletonList(new MavenInstaller(null));
            }

            // overriding them for backward compatibility.
            // newer code need not do this
            @Override
            public MavenInstallation[] getInstallations() {
                return Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
            }

            // overriding them for backward compatibility.
            // newer code need not do this
            @Override
            public void setInstallations(MavenInstallation... installations) {
                Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(installations);
            }

            /**
             * Checks if the MAVEN_HOME is valid.
             */
            @Override protected FormValidation checkHomeDirectory(File value) {
                File maven1File = new File(value,MAVEN_1_INSTALLATION_COMMON_FILE);
                File maven2File = new File(value,MAVEN_2_INSTALLATION_COMMON_FILE);

                if(!maven1File.exists() && !maven2File.exists())
                    return FormValidation.error(Messages.Maven_NotMavenDirectory(value));

                return FormValidation.ok();
            }

        }

        public static class ConverterImpl extends ToolConverter {
            public ConverterImpl(XStream2 xstream) { super(xstream); }
            @Override protected String oldHomeField(ToolInstallation obj) {
                return ((MavenInstallation)obj).mavenHome;
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

        @Extension @Symbol("maven")
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
