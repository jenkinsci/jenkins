package hudson.maven;

import hudson.tasks.Maven.MavenInstallation;
import hudson.model.JDK;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Executor;
import hudson.model.Run.RunnerAbortedException;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.remoting.Callable;
import static hudson.Util.fixNull;
import hudson.util.IOException2;
import hudson.util.ArgumentListBuilder;
import hudson.FilePath;
import hudson.Launcher;
import hudson.maven.agent.Main;

import java.io.OutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.net.URLConnection;
import java.net.JarURLConnection;
import java.util.Map;

/**
 * Launches the maven process.
 *
 * @author Kohsuke Kawaguchi
 */
final class MavenProcessFactory implements ProcessCache.Factory {
    private final MavenModuleSet mms;
    private final Launcher launcher;
    /**
     * Environment variables to be set to the maven process.
     * The same variables are exposed to the system property as well.
     */
    private final Map<String,String> envVars;

    MavenProcessFactory(MavenModuleSet mms, Launcher launcher, Map<String, String> envVars) {
        this.mms = mms;
        this.launcher = launcher;
        this.envVars = envVars;
    }

    /**
     * Starts maven process.
     */
    public Channel newProcess(BuildListener listener, OutputStream out) throws IOException, InterruptedException {
        if(debug)
            listener.getLogger().println("Using env variables: "+ envVars);
        try {
            return launcher.launchChannel(buildMavenCmdLine(listener).toCommandArray(),
                out, null, envVars);
        } catch (IOException e) {
            if(fixNull(e.getMessage()).contains("java: not found")) {
                // diagnose issue #659
                JDK jdk = mms.getJDK();
                if(jdk==null)
                    throw new IOException2(mms.getDisplayName()+" is not configured with a JDK, but your PATH doesn't include Java",e);
            }
            throw e;
        }
    }

    /**
     * Builds the command line argument list to launch the maven process.
     *
     * UGLY.
     */
    private ArgumentListBuilder buildMavenCmdLine(BuildListener listener) throws IOException, InterruptedException {
        MavenInstallation mvn = getMavenInstallation();
        if(mvn==null) {
            listener.error("Maven version is not configured for this project. Can't determine which Maven to run");
            throw new RunnerAbortedException();
        }

        // find classworlds.jar
        File bootDir = new File(mvn.getHomeDir(), "core/boot");
        File[] classworlds = bootDir.listFiles(CLASSWORLDS_FILTER);
        if(classworlds==null || classworlds.length==0) {
            // Maven 2.0.6 puts it to a different place
            bootDir = new File(mvn.getHomeDir(), "boot");
            classworlds = bootDir.listFiles(CLASSWORLDS_FILTER);
            if(classworlds==null || classworlds.length==0) {
                listener.error("No classworlds*.jar found in "+mvn.getHomeDir()+" -- Is this a valid maven2 directory?");
                throw new RunnerAbortedException();
            }
        }

        boolean isMaster = getCurrentNode()== Hudson.getInstance();
        FilePath slaveRoot=null;
        if(!isMaster)
            slaveRoot = getCurrentNode().getRootPath();

        ArgumentListBuilder args = new ArgumentListBuilder();
        JDK jdk = mms.getJDK();
        if(jdk==null)
            args.add("java");
        else
            args.add(jdk.getJavaHome()+"/bin/java");

        if(debugPort!=0)
            args.add("-Xrunjdwp:transport=dt_socket,server=y,address="+debugPort);

        args.addTokenized(getMavenOpts());

        args.add("-cp");
        args.add(
            (isMaster? Which.jarFile(Main.class).getAbsolutePath():slaveRoot.child("maven-agent.jar").getRemote())+
            (launcher.isUnix()?":":";")+
            classworlds[0].getAbsolutePath());
        args.add(Main.class.getName());

        // M2_HOME
        args.add(mvn.getMavenHome());

        // remoting.jar
        args.add(launcher.getChannel().call(new GetRemotingJar()));
        // interceptor.jar
        args.add(isMaster?
            Which.jarFile(hudson.maven.agent.PluginManagerInterceptor.class).getAbsolutePath():
            slaveRoot.child("maven-interceptor.jar").getRemote());
        return args;
    }

    public String getMavenOpts() {
        return mms.getMavenOpts();
    }

    public MavenInstallation getMavenInstallation() {
        return mms.getMaven();
    }

    public JDK getJava() {
        return mms.getJDK();
    }

    private static final class GetRemotingJar implements Callable<String,IOException> {
        public String call() throws IOException {
            URL classFile = Main.class.getClassLoader().getResource(hudson.remoting.Launcher.class.getName().replace('.','/')+".class");

            // JNLP returns the URL where the jar was originally placed (like http://hudson.dev.java.net/...)
            // not the local cached file. So we need a rather round about approach to get to
            // the local file name.
            URLConnection con = classFile.openConnection();
            if (con instanceof JarURLConnection) {
                JarURLConnection connection = (JarURLConnection) con;
                return connection.getJarFile().getName();
            }

            return Which.jarFile(hudson.remoting.Launcher.class).getPath();
        }
    }

    /**
     * Returns the current {@link Node} on which we are buildling.
     */
    private Node getCurrentNode() {
        return Executor.currentExecutor().getOwner().getNode();
    }

    private static final FilenameFilter CLASSWORLDS_FILTER = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.startsWith("classworlds") && name.endsWith(".jar");
        }
    };

    /**
     * Set true to produce debug output.
     */
    public static boolean debug = false;

    /**
     * If not 0, launch Maven with a debugger port.
     */
    public static int debugPort;

    static {
        String port = System.getProperty(MavenBuild.class.getName() + ".debugPort");
        if(port!=null)
            debugPort = Integer.parseInt(port);
    }
}
