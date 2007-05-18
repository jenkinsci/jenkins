package hudson.tasks;

import hudson.CopyOnWrite;
import hudson.FilePath.FileCallable;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormFieldValidator;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.StringTokenizer;

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
    private final String targets;

    /**
     * Identifies {@link MavenInstallation} to be used.
     */
    private final String mavenName;

    public Maven(String targets,String mavenName) {
        this.targets = targets;
        this.mavenName = mavenName;
    }

    public String getTargets() {
        return targets;
    }

    /**
     * Gets the Maven to invoke,
     * or null to invoke the default one.
     */
    public MavenInstallation getMaven() {
        for( MavenInstallation i : DESCRIPTOR.getInstallations() ) {
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
                if(new File(ws,"pom.xml").exists())
                    seed = "mvn";
                else
                    // err on Maven 1 to be closer to the behavior in < 1.81
                    seed = "maven";
            }

            if(Functions.isWindows())
                seed += ".bat";
            return seed;
        }
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        Project proj = build.getProject();

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
            MavenInstallation ai = getMaven();
            if(ai==null) {
                String execName = proj.getWorkspace().act(new DecideDefaultMavenCommand(normalizedTarget));
                args.add(execName).addTokenized(normalizedTarget);
            } else {
                File exec = ai.getExecutable();
                if(exec==null) {
                    listener.fatalError("Couldn't find any executable in "+ai.getMavenHome());
                    return false;
                }
                if(!exec.exists()) {
                    listener.fatalError(exec+" doesn't exist");
                    return false;
                }
                args.add(exec.getPath()).addTokenized(normalizedTarget);
            }

            Map<String,String> env = build.getEnvVars();
            if(ai!=null) {
                // if somebody has use M2_HOME they will get a classloading error
                // when M2_HOME points to a different version of Maven2 from
                // MAVEN_HOME (as Maven 2 gives M2_HOME priority)
                // 
                // The other solution would be to set M2_HOME if we are calling Maven2 
                // and MAVEN_HOME for Maven1 (only of use for strange people that
                // are calling Maven2 from Maven1)
                env.remove("M2_HOME");
                env.put("MAVEN_HOME",ai.getMavenHome());
            }
            // just as a precaution
            // see http://maven.apache.org/continuum/faqs.html#how-does-continuum-detect-a-successful-build
            env.put("MAVEN_TERMINATE_CMD","on");

            try {
                int r = launcher.launch(args.toCommandArray(),env,listener.getLogger(),proj.getModuleRoot()).join();
                if (0 != r) {
                    return false;
                }
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError("command execution failed") );
                return false;
            }
            startIndex = endIndex + 1;
        } while (startIndex < targets.length());
        return true;
    }

    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Builder> {
        @CopyOnWrite
        private volatile MavenInstallation[] installations = new MavenInstallation[0];

        private DescriptorImpl() {
            super(Maven.class);
            load();
        }


        protected void convert(Map<String, Object> oldPropertyBag) {
            if(oldPropertyBag.containsKey("installations"))
                installations = (MavenInstallation[]) oldPropertyBag.get("installations");
        }

        public String getHelpFile() {
            return "/help/project-config/maven.html";
        }

        public String getDisplayName() {
            return "Invoke top-level Maven targets";
        }

        public MavenInstallation[] getInstallations() {
            return installations;
        }

        public boolean configure(StaplerRequest req) {
            boolean r = true;

            int i;
            String[] names = req.getParameterValues("maven_name");
            String[] homes = req.getParameterValues("maven_home");
            int len;
            if(names!=null && homes!=null)
                len = Math.min(names.length,homes.length);
            else
                len = 0;
            MavenInstallation[] insts = new MavenInstallation[len];

            for( i=0; i<len; i++ ) {
                if(Util.nullify(names[i])==null)    continue;
                if(Util.nullify(homes[i])==null)    continue;
                insts[i] = new MavenInstallation(names[i],homes[i]);
            }

            this.installations = insts;

            save();

            return r;
        }

        public Builder newInstance(StaplerRequest req) {
            return new Maven(req.getParameter("maven_targets"),req.getParameter("maven_version"));
        }


    //
    // web methods
    //
        /**
         * Checks if the MAVEN_HOME is valid.
         */
        public void doCheckMavenHome( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
            // this can be used to check the existence of a file on the server, so needs to be protected
            new FormFieldValidator(req,rsp,true) {
                public void check() throws IOException, ServletException {
                    File f = getFileParameter("value");
                    if(f.getPath().equals("")) {
                        error("MAVEN_HOME is required");
                        return;
                    }
                    if(!f.isDirectory()) {
                        error(f+" is not a directory");
                        return;
                    }

                    // I couldn't come up with a simple logic to test for a maven installation
                    // there seems to be just too much difference between m1 and m2.

                    ok();
                }
            }.process();
        }
    }

    public static final class MavenInstallation {
        private final String name;
        private final String mavenHome;

        public MavenInstallation(String name, String mavenHome) {
            this.name = name;
            this.mavenHome = mavenHome;
        }

        /**
         * install directory.
         */
        public String getMavenHome() {
            return mavenHome;
        }

        public File getHomeDir() {
            return new File(mavenHome);
        }

        /**
         * Human readable display name.
         */
        public String getName() {
            return name;
        }

        public File getExecutable() {
            File exe = getExeFile("maven");
            if(exe.exists())
                return exe;
            exe = getExeFile("mvn");
            if(exe.exists())
                return exe;
            return null;
        }

        private File getExeFile(String execName) {
            if(File.separatorChar=='\\')
                execName += ".bat";
            return new File(getMavenHome(), "bin/" + execName);
        }

        /**
         * Returns true if the executable exists.
         */
        public boolean getExists() {
            return getExecutable()!=null;
        }
    }
}
