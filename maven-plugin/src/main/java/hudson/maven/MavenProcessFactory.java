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
package hudson.maven;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.slaves.Channels;
import static hudson.Util.fixNull;
import hudson.maven.agent.Main;
import hudson.maven.agent.Maven21Interceptor;
import hudson.maven.ProcessCache.NewProcess;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.Run.RunnerAbortedException;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.SocketInputStream;
import hudson.remoting.SocketOutputStream;
import hudson.remoting.Which;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks._maven.MavenConsoleAnnotator;
import hudson.util.ArgumentListBuilder;
import hudson.util.IOException2;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.logging.Logger;


/**
 * Launches the maven process.
 *
 * @author Kohsuke Kawaguchi
 */
final class MavenProcessFactory extends AbstractMavenProcessFactory implements ProcessCache.Factory {


    MavenProcessFactory(MavenModuleSet mms, Launcher launcher, EnvVars envVars, String mavenOpts, FilePath workDir) {
        super( mms, launcher, envVars, mavenOpts, workDir );
    }

    /**
     * Builds the command line argument list to launch the maven process.
     *
     * UGLY.
     */
    protected ArgumentListBuilder buildMavenAgentCmdLine(BuildListener listener,int tcpPort) throws IOException, InterruptedException {
        MavenInstallation mvn = getMavenInstallation(listener);
        if(mvn==null) {
            listener.error("Maven version is not configured for this project. Can't determine which Maven to run");
            throw new RunnerAbortedException();
        }
        if(mvn.getHome()==null) {
            listener.error("Maven '%s' doesn't have its home set",mvn.getName());
            throw new RunnerAbortedException();
        }

        // find classworlds.jar
        String classWorldsJar = getLauncher().getChannel().call(new GetClassWorldsJar(mvn.getHome(),listener));

        boolean isMaster = getCurrentNode()== Hudson.getInstance();
        FilePath slaveRoot=null;
        if(!isMaster)
            slaveRoot = getCurrentNode().getRootPath();

        ArgumentListBuilder args = new ArgumentListBuilder();
        JDK jdk = getJava(listener);
        if(jdk==null) {
            args.add("java");
        } else {
            args.add(jdk.getHome()+"/bin/java"); // use JDK.getExecutable() here ?
        }

        if(debugPort!=0)
            args.add("-Xrunjdwp:transport=dt_socket,server=y,address="+debugPort);
        if(yjp)
            args.add("-agentlib:yjpagent=tracing");

        args.addTokenized(getMavenOpts());
        
        args.add( "-cp" );
        String classPath =
            ( isMaster ? Which.jarFile( Main.class ).getAbsolutePath()
                            : slaveRoot.child( "maven-agent.jar" ).getRemote() )
                + ( getLauncher().isUnix() ? ":" : ";" )
                + ( isMaster ? classWorldsJar : slaveRoot.child( "classworlds.jar" ).getRemote() );
        args.add( classPath );
            //+classWorldsJar);
        args.add(Main.class.getName());

        // M2_HOME
        args.add(mvn.getHome());

        // remoting.jar
        String remotingJar = getLauncher().getChannel().call(new GetRemotingJar());
        if(remotingJar==null) {// this shouldn't be possible, but there are still reports indicating this, so adding a probe here.
            listener.error("Failed to determine the location of slave.jar");
            throw new RunnerAbortedException();
        }
        args.add(remotingJar);

        // interceptor.jar
        args.add(isMaster?
            Which.jarFile(hudson.maven.agent.AbortException.class).getAbsolutePath():
            slaveRoot.child("maven-interceptor.jar").getRemote());

        // TCP/IP port to establish the remoting infrastructure
        args.add(tcpPort);

        // if this is Maven 2.1, interceptor override
        if(mvn.isMaven2_1(getLauncher())) {
            args.add(isMaster?
                Which.jarFile(Maven21Interceptor.class).getAbsolutePath():
                slaveRoot.child("maven2.1-interceptor.jar").getRemote());
        }
       
        return args;
    }
    
    /**
     * Finds classworlds.jar
     */
    private static final class GetClassWorldsJar implements Callable<String,IOException> {
        private final String mvnHome;
        private final TaskListener listener;

        private GetClassWorldsJar(String mvnHome, TaskListener listener) {
            this.mvnHome = mvnHome;
            this.listener = listener;
        }

        public String call() throws IOException {
            File home = new File(mvnHome);
            File bootDir = new File(home, "core/boot");
            File[] classworlds = bootDir.listFiles(CLASSWORLDS_FILTER);
            if(classworlds==null || classworlds.length==0) {
                // Maven 2.0.6 puts it to a different place
                bootDir = new File(home, "boot");
                classworlds = bootDir.listFiles(CLASSWORLDS_FILTER);
                if(classworlds==null || classworlds.length==0) {
                    listener.error(Messages.MavenProcessFactory_ClassWorldsNotFound(home));
                    throw new RunnerAbortedException();
                }
            }
            return classworlds[0].getAbsolutePath();
        }
    }
    
    /**
     * Locates classworlds jar file.
     *
     * Note that Maven 3.0 changed the name to plexus-classworlds 
     *
     * <pre>
     * $ find tools/ -name "*classworlds*.jar"
     * tools/maven/boot/classworlds-1.1.jar
     * tools/maven-2.2.1/boot/classworlds-1.1.jar
     * tools/maven-3.0-alpha-2/boot/plexus-classworlds-1.3.jar
     * tools/maven-3.0-alpha-3/boot/plexus-classworlds-2.2.2.jar
     * tools/maven-3.0-alpha-4/boot/plexus-classworlds-2.2.2.jar
     * tools/maven-3.0-alpha-5/boot/plexus-classworlds-2.2.2.jar
     * tools/maven-3.0-alpha-6/boot/plexus-classworlds-2.2.2.jar
     * </pre>
     */
    private static final FilenameFilter CLASSWORLDS_FILTER = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.contains("classworlds") && name.endsWith(".jar");
        }
    };
    
    //-------------------------------------------------
    // Some of those fields are used for maven 3 too
    //-------------------------------------------------

    /**
     * Set true to produce debug output.
     */
    public static boolean debug = false;

    /**
     * If not 0, launch Maven with a debugger port.
     */
    public static int debugPort;

    public static boolean profile = Boolean.getBoolean("hudson.maven.profile");
    
    /**
     * If true, launch Maven with YJP offline profiler agent.
     */
    public static boolean yjp = Boolean.getBoolean("hudson.maven.yjp");

    static {
        String port = System.getProperty("hudson.maven.debugPort");
        if(port!=null)
            debugPort = Integer.parseInt(port);
    }
    
    public static int socketTimeOut = Integer.parseInt( System.getProperty( "hudson.maven.socketTimeOut", Integer.toString( 30*1000 ) ) );
       

    private static final Logger LOGGER = Logger.getLogger(MavenProcessFactory.class.getName());
}
