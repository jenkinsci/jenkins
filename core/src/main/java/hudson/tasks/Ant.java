package hudson.tasks;

import hudson.CopyOnWrite;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormFieldValidator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

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
        this.antOpts = Util.fixEmpty(antOpts.trim());
        this.buildFile = Util.fixEmpty(buildFile.trim());
        this.properties = Util.fixEmpty(properties);
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
        for( AntInstallation i : DESCRIPTOR.getInstallations() ) {
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

    public boolean perform(Build<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        Project proj = build.getProject();

        ArgumentListBuilder args = new ArgumentListBuilder();

        String execName;
        if(launcher.isUnix())
            execName = "ant";
        else
            execName = "ant.bat";

        String normalizedTarget = targets.replaceAll("[\t\r\n]+"," ");

        AntInstallation ai = getAnt();
        if(ai==null) {
            args.add(execName);
        } else {
            File exec = ai.getExecutable(launcher.isUnix());
            args.add(exec.getPath());
        }
        
        if(buildFile!=null) {
        	args.add("-file", buildFile);
        }

        args.addKeyValuePairs("-D",build.getBuildVariables());

        if (properties != null) {
            final Properties p = new Properties();
            final Reader reader = new StringReader(properties);
            try {
                p.load(reader);
            }
            finally {
                reader.close();
            }

            for (final Entry<Object, Object> entry : p.entrySet()) {
                args.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
        }

        args.addTokenized(normalizedTarget);

        Map<String,String> env = build.getEnvVars();
        if(ai!=null)
            env.put("ANT_HOME",ai.getAntHome());
        if(antOpts!=null)
            env.put("ANT_OPTS",antOpts);

        if(!launcher.isUnix()) {
            // on Windows, executing batch file can't return the correct error code,
            // so we need to wrap it into cmd.exe.
            // double %% is needed because we want ERRORLEVEL to be expanded after
            // batch file executed, not before. This alone shows how broken Windows is...
            args.prepend("cmd.exe","/C");
            args.add("&&","exit","%%ERRORLEVEL%%");
        }

        try {
            int r = launcher.launch(args.toCommandArray(),env,listener.getLogger(),proj.getModuleRoot()).join();
            return r==0;
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace( listener.fatalError("command execution failed") );
            return false;
        }
    }

    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Builder> {
        @CopyOnWrite
        private volatile AntInstallation[] installations = new AntInstallation[0];

        private DescriptorImpl() {
            super(Ant.class);
            load();
        }

        protected void convert(Map<String,Object> oldPropertyBag) {
            if(oldPropertyBag.containsKey("installations"))
                installations = (AntInstallation[]) oldPropertyBag.get("installations");
        }

        public String getHelpFile() {
            return "/help/project-config/ant.html";
        }

        public String getDisplayName() {
            return "Invoke Ant";
        }

        public AntInstallation[] getInstallations() {
            return installations;
        }

        public boolean configure(StaplerRequest req) {
            boolean r = true;

            int i;
            String[] names = req.getParameterValues("ant_name");
            String[] homes = req.getParameterValues("ant_home");
            int len;
            if(names!=null && homes!=null)
                len = Math.min(names.length,homes.length);
            else
                len = 0;
            AntInstallation[] insts = new AntInstallation[len];

            for( i=0; i<len; i++ ) {
                if(names[i].length()==0 || homes[i].length()==0)    continue;
                insts[i] = new AntInstallation(names[i],homes[i]);
            }

            this.installations = insts;

            save();

            return r;
        }

        public Builder newInstance(StaplerRequest req) {
            return req.bindParameters(Ant.class,"ant.");
        }

    //
    // web methods
    //
        /**
         * Checks if the ANT_HOME is valid.
         */
        public void doCheckAntHome( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
            // this can be used to check the existence of a file on the server, so needs to be protected
            new FormFieldValidator(req,rsp,true) {
                public void check() throws IOException, ServletException {
                    File f = getFileParameter("value");
                    if(!f.isDirectory()) {
                        error(f+" is not a directory");
                        return;
                    }

                    File antJar = new File(f,"lib/ant.jar");
                    if(!antJar.exists()) {
                        error(f+" doesn't look like an Ant directory");
                        return;
                    }

                    ok();
                }
            }.process();
        }
    }

    public static final class AntInstallation {
        private final String name;
        private final String antHome;

        public AntInstallation(String name, String antHome) {
            this.name = name;
            this.antHome = antHome;
        }

        /**
         * install directory.
         */
        public String getAntHome() {
            return antHome;
        }

        /**
         * Human readable display name.
         */
        public String getName() {
            return name;
        }

        public File getExecutable(boolean isUnix) {
            String execName;
            if(isUnix)
                execName = "ant";
            else
                execName = "ant.bat";

            return new File(getAntHome(),"bin/"+execName);
        }

        /**
         * Returns true if the executable exists.
         */
        public boolean getExists() {
            return getExecutable(!Hudson.isWindows()).exists();
        }
    }
}
