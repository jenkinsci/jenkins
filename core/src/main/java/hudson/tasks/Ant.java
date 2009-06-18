/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.remoting.Callable;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.ArgumentListBuilder;
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
import java.util.List;
import java.util.Collections;

/**
 * Ant launcher.
 *
 * @author Kohsuke Kawaguchi
 */
public class Ant extends Builder {
    /**
     * The targets, properties, and other Ant options.
     * Either separated by whitespace or newline.
     */
    private final String targets;

    /**
     * Identifies {@link AntInstallation} to be used.
     */
    private final String antName;

    /**
     * ANT_OPTS if not null.
     */
    private final String antOpts;

    /**
     * Optional build script path relative to the workspace.
     * Used for the Ant '-f' option.
     */
    private final String buildFile;

    /**
     * Optional properties to be passed to Ant. Follows {@link Properties} syntax.
     */
    private final String properties;
    
    @DataBoundConstructor
    public Ant(String targets,String antName, String antOpts, String buildFile, String properties) {
        this.targets = targets;
        this.antName = antName;
        this.antOpts = Util.fixEmptyAndTrim(antOpts);
        this.buildFile = Util.fixEmptyAndTrim(buildFile);
        this.properties = Util.fixEmptyAndTrim(properties);
    }

	public String getBuildFile() {
		return buildFile;
	}

	public String getProperties() {
		return properties;
	}

	public String getTargets() {
        return targets;
    }

    /**
     * Gets the Ant to invoke,
     * or null to invoke the default one.
     */
    public AntInstallation getAnt() {
        for( AntInstallation i : getDescriptor().getInstallations() ) {
            if(antName!=null && i.getName().equals(antName))
                return i;
        }
        return null;
    }

    /**
     * Gets the ANT_OPTS parameter, or null.
     */
    public String getAntOpts() {
        return antOpts;
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        AbstractProject proj = build.getProject();

        
        ArgumentListBuilder args = new ArgumentListBuilder();

        EnvVars env = build.getEnvironment(listener);
        
        AntInstallation ai = getAnt();
        if(ai==null) {
            args.add(launcher.isUnix() ? "ant" : "ant.bat");
        } else {
            ai = ai.forNode(Computer.currentComputer().getNode(), listener);
            ai = ai.forEnvironment(env);
            String exe = ai.getExecutable(launcher);
            if (exe==null) {
                listener.fatalError(Messages.Ant_ExecutableNotFound(ai.getName()));
                return false;
            }
            args.add(exe);
        }

        VariableResolver<String> vr = build.getBuildVariableResolver();

        String buildFile = env.expand(this.buildFile);
        String targets = Util.replaceMacro(env.expand(this.targets), vr);
        
        FilePath buildFilePath = buildFilePath(proj.getModuleRoot(), buildFile, targets);

        if(!buildFilePath.exists()) {
            // because of the poor choice of getModuleRoot() with CVS/Subversion, people often get confused
            // with where the build file path is relative to. Now it's too late to change this behavior
            // due to compatibility issue, but at least we can make this less painful by looking for errors
            // and diagnosing it nicely. See HUDSON-1782

            // first check if this appears to be a valid relative path from workspace root
            FilePath buildFilePath2 = buildFilePath(proj.getWorkspace(), buildFile, targets);
            if(buildFilePath2.exists()) {
                // This must be what the user meant. Let it continue.
                buildFilePath = buildFilePath2;
            } else {
                // neither file exists. So this now really does look like an error.
                listener.fatalError("Unable to find build script at "+buildFilePath);
                return false;
            }
        }

        if(buildFile!=null) {
            args.add("-file", buildFilePath.getName());
        }

        args.addKeyValuePairs("-D",build.getBuildVariables());

        args.addKeyValuePairsFromPropertyString("-D",properties,vr);

        args.addTokenized(targets.replaceAll("[\t\r\n]+"," "));

        if(ai!=null)
            env.put("ANT_HOME",ai.getHome());
        if(antOpts!=null)
            env.put("ANT_OPTS",env.expand(antOpts));

        if(!launcher.isUnix()) {
            // on Windows, executing batch file can't return the correct error code,
            // so we need to wrap it into cmd.exe.
            // double %% is needed because we want ERRORLEVEL to be expanded after
            // batch file executed, not before. This alone shows how broken Windows is...
            args.add("&&","exit","%%ERRORLEVEL%%");

            // on Windows, proper double quote handling requires extra surrounding quote.
            // so we need to convert the entire argument list once into a string,
            // then build the new list so that by the time JVM invokes CreateProcess win32 API,
            // it puts additional double-quote. See issue #1007
            // the 'addQuoted' is necessary because Process implementation for Windows (at least in Sun JVM)
            // is too clever to avoid putting a quote around it if the argument begins with "
            // see "cmd /?" for more about how cmd.exe handles quotation.
            args = new ArgumentListBuilder().add("cmd.exe","/C").addQuoted(args.toStringWithQuote());
        }

        long startTime = System.currentTimeMillis();
        try {
            int r = launcher.launch().cmds(args).envs(env).stdout(listener).pwd(buildFilePath.getParent()).join();
            return r==0;
        } catch (IOException e) {
            Util.displayIOException(e,listener);

            String errorMessage = Messages.Ant_ExecFailed();
            if(ai==null && (System.currentTimeMillis()-startTime)<1000) {
                if(getDescriptor().getInstallations()==null)
                    // looks like the user didn't configure any Ant installation
                    errorMessage += Messages.Ant_GlobalConfigNeeded();
                else
                    // There are Ant installations configured but the project didn't pick it
                    errorMessage += Messages.Ant_ProjectConfigNeeded();
            }
            e.printStackTrace( listener.fatalError(errorMessage) );
            return false;
        }
    }

    private static FilePath buildFilePath(FilePath base, String buildFile, String targets) {
        if(buildFile!=null)     return base.child(buildFile);
        // some users specify the -f option in the targets field, so take that into account as well.
        // see 
        String[] tokens = Util.tokenize(targets);
        for (int i = 0; i<tokens.length-1; i++) {
            String a = tokens[i];
            if(a.equals("-f") || a.equals("-file") || a.equals("-buildfile"))
                return base.child(tokens[i+1]);
        }
        return base.child("build.xml");
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @CopyOnWrite
        private volatile AntInstallation[] installations = new AntInstallation[0];

        public DescriptorImpl() {
            load();
        }

        protected DescriptorImpl(Class<? extends Ant> clazz) {
            super(clazz);
        }

        /**
         * Obtains the {@link AntInstallation.DescriptorImpl} instance.
         */
        public AntInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(AntInstallation.DescriptorImpl.class);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        protected void convert(Map<String,Object> oldPropertyBag) {
            if(oldPropertyBag.containsKey("installations"))
                installations = (AntInstallation[]) oldPropertyBag.get("installations");
        }

        public String getHelpFile() {
            return "/help/project-config/ant.html";
        }

        public String getDisplayName() {
            return Messages.Ant_DisplayName();
        }

        public AntInstallation[] getInstallations() {
            return installations;
        }

        public Ant newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return (Ant)req.bindJSON(clazz,formData);
        }

        public void setInstallations(AntInstallation... antInstallations) {
            this.installations = antInstallations;
            save();
        }
    }

    /**
     * Represents the Ant installation on the system.
     */
    public static final class AntInstallation extends ToolInstallation implements
            EnvironmentSpecific<AntInstallation>, NodeSpecific<AntInstallation> {
        // to remain backward compatible with earlier Hudson that stored this field here.
        private final String antHome;

        @DataBoundConstructor
        public AntInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
            super(name, launderHome(home), properties);
            this.antHome = super.getHome();
        }

        /**
         * @deprecated as of 1.308
         *      Use {@link #AntInstallation(String, String, List)}
         */
        public AntInstallation(String name, String home) {
            this(name,home,Collections.<ToolProperty<?>>emptyList());
        }

        private static String launderHome(String home) {
            if(home.endsWith("/") || home.endsWith("\\")) {
                // see https://issues.apache.org/bugzilla/show_bug.cgi?id=26947
                // Ant doesn't like the trailing slash, especially on Windows
                return home.substring(0,home.length()-1);
            } else {
                return home;
            }
        }

        /**
         * install directory.
         *
         * @deprecated as of 1.307. Use {@link #getHome()}.
         */
        public String getAntHome() {
            return getHome();
        }

        public String getHome() {
            if (antHome != null) return antHome;
            return super.getHome();
        }

        /**
         * Gets the executable path of this Ant on the given target system.
         */
        public String getExecutable(Launcher launcher) throws IOException, InterruptedException {
            return launcher.getChannel().call(new Callable<String,IOException>() {
                public String call() throws IOException {
                    File exe = getExeFile();
                    if(exe.exists())
                        return exe.getPath();
                    return null;
                }
            });
        }

        private File getExeFile() {
            String execName;
            if(Hudson.isWindows())
                execName = "ant.bat";
            else
                execName = "ant";

            String antHome = Util.replaceMacro(getAntHome(),EnvVars.masterEnvVars);

            return new File(antHome,"bin/"+execName);
        }

        /**
         * Returns true if the executable exists.
         */
        public boolean getExists() throws IOException, InterruptedException {
            return getExecutable(new Launcher.LocalLauncher(TaskListener.NULL))!=null;
        }

        private static final long serialVersionUID = 1L;

        public AntInstallation forEnvironment(EnvVars environment) {
            return new AntInstallation(getName(), environment.expand(antHome), getProperties().toList());
        }

        public AntInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
            return new AntInstallation(getName(), translateFor(node, log), getProperties().toList());
        }

        @Extension
        public static class DescriptorImpl extends ToolDescriptor<AntInstallation> {

            @Override
            public String getDisplayName() {
                return "Ant";
            }

            // for compatibility reasons, the persistence is done by Ant.DescriptorImpl  
            @Override
            public AntInstallation[] getInstallations() {
                return Hudson.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).getInstallations();
            }

            @Override
            public void setInstallations(AntInstallation... installations) {
                Hudson.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).setInstallations(installations);
            }

            @Override
            public List<? extends ToolInstaller> getDefaultInstallers() {
                return Collections.singletonList(new AntInstaller(null));
            }

            @Override
            public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
                setInstallations(req.bindJSONToList(
                        AntInstallation.class, json.get("ant")).toArray(new AntInstallation[0]));
                return true;
            }

            /**
             * Checks if the ANT_HOME is valid.
             */
            public FormValidation doCheckHome(@QueryParameter File value) {
                // this can be used to check the existence of a file on the server, so needs to be protected
                if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER))
                    return FormValidation.ok();

                if(value.getPath().equals(""))
                    return FormValidation.ok();

                if(!value.isDirectory())
                    return FormValidation.error(Messages.Ant_NotADirectory(value));

                File antJar = new File(value,"lib/ant.jar");
                if(!antJar.exists())
                    return FormValidation.error(Messages.Ant_NotAntDirectory(value));

                return FormValidation.ok();
            }
        }
    }

    /**
     * Automatic Ant installer from apache.org.
     */
    public static class AntInstaller extends DownloadFromUrlInstaller {
        @DataBoundConstructor
        public AntInstaller(String id) {
            super(id);
        }

        @Extension
        public static final class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<AntInstaller> {
            public String getDisplayName() {
                return Messages.InstallFromApache();
            }

            @Override
            public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
                return toolType==AntInstallation.class;
            }
        }
    }
}
