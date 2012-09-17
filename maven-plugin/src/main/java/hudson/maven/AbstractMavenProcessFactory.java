package hudson.maven;

import static hudson.Util.fixNull;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Platform;
import hudson.Proc;
import hudson.maven.ProcessCache.NewProcess;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.Run.RunnerAbortedException;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.SocketInputStream;
import hudson.remoting.SocketOutputStream;
import hudson.remoting.Which;
import hudson.slaves.Channels;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks._maven.MavenConsoleAnnotator;
import hudson.util.ArgumentListBuilder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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

import jenkins.model.Jenkins;

import org.kohsuke.stapler.framework.io.IOException2;

/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Olivier Lamy
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

/**
 * @author Olivier Lamy
 */
public abstract class AbstractMavenProcessFactory
{

    private final MavenModuleSet mms;
    private final Launcher launcher;
    /**
     * Environment variables to be set to the maven process.
     * The same variables are exposed to the system property as well.
     */
    private final EnvVars envVars;

    /**
     * Optional working directory. Because of the process reuse, we can't always guarantee
     * that the returned Maven process has this as the working directory. But for the
     * aggregator style build, the process reuse is disabled, so in practice this always works.
     *
     * Also, Maven is supposed to work correctly regardless of the process current directory,
     * so a good behaving maven project shouldn't rely on the current project.
     */
    private final FilePath workDir;

    private final String mavenOpts;

    AbstractMavenProcessFactory(MavenModuleSet mms, Launcher launcher, EnvVars envVars, String mavenOpts, FilePath workDir) {
        this.mms = mms;
        this.launcher = launcher;
        this.envVars = envVars;
        this.workDir = workDir;
        this.mavenOpts = mavenOpts;
    }

    /**
     * Represents a bi-directional connection.
     *
     * <p>
     * This implementation is remoting aware, so it can be safely sent to the remote callable object.
     *
     * <p>
     * When we run Maven on a slave, the master may not have a direct TCP/IP connectivty to the slave.
     * That means the {@link Channel} between the master and the Maven needs to be tunneled through
     * the channel between master and the slave, then go to TCP socket to the Maven.
     */
    private static final class Connection implements Serializable {
        public InputStream in;
        public OutputStream out;

        Connection(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        private Object writeReplace() {
            return new Connection(new RemoteInputStream(in),new RemoteOutputStream(out));
        }

        private Object readResolve() {
            // ObjectInputStream seems to access data at byte-level and do not do any buffering,
            // so if we are remoted, buffering would be crucial.
            this.in = new BufferedInputStream(in);
            this.out = new BufferedOutputStream(out);
            return this;
        }

        private static final long serialVersionUID = 1L;
    }

    interface Acceptor {
        Connection accept() throws IOException;
        int getPort();
    }

    /**
     * Opens a server socket and returns {@link Acceptor} so that
     * we can accept a connection later on it.
     */
    private static final class SocketHandler implements Callable<Acceptor,IOException> {
        public Acceptor call() throws IOException {
            return new AcceptorImpl();
        }

        private static final long serialVersionUID = 1L;

        static final class AcceptorImpl implements Acceptor, Serializable {
            private static final long serialVersionUID = -2226788819948521018L;
            private transient final ServerSocket serverSocket;
            private transient Socket socket;

            AcceptorImpl() throws IOException {
                // open a TCP socket to talk to the launched Maven process.
                // let the OS pick up a random open port
                this.serverSocket = new ServerSocket();
                serverSocket.bind(null); // new InetSocketAddress(InetAddress.getLocalHost(),0));
                // prevent a hang at the accept method in case the forked process didn't start successfully
                serverSocket.setSoTimeout(MavenProcessFactory.socketTimeOut);
            }

            public Connection accept() throws IOException {
                socket = serverSocket.accept();
                // we'd only accept one connection
                serverSocket.close();

                return new Connection(new SocketInputStream(socket),new SocketOutputStream(socket));
            }

            public int getPort() {
                return serverSocket.getLocalPort();
            }

            /**
             * When sent to the remote node, send a proxy.
             */
            private Object writeReplace() {
                return Channel.current().export(Acceptor.class, this);
            }
        }
    }
    
    private static final class GetCharset implements Callable<String,IOException> {
        private static final long serialVersionUID = 3459269768733083577L;

        public String call() throws IOException {
            return System.getProperty("file.encoding");
        }
    }    

    /**
     * Starts maven process.
     */
    public ProcessCache.NewProcess newProcess(BuildListener listener, OutputStream out) throws IOException, InterruptedException {
        if(MavenProcessFactory.debug)
            listener.getLogger().println("Using env variables: "+ envVars);
        try {
            //launcher.getChannel().export( type, instance )
            final Acceptor acceptor = launcher.getChannel().call(new SocketHandler());
            Charset charset;
            try {
                charset = Charset.forName(launcher.getChannel().call(new GetCharset()));
            } catch (UnsupportedCharsetException e) {
                // choose the bit preserving charset. not entirely sure if iso-8859-1 does that though.
                charset = Charset.forName("iso-8859-1");
            }

            MavenConsoleAnnotator mca = new MavenConsoleAnnotator(out,charset);

            if ( mavenRemoteUseInet ) {
                envVars.put(MAVEN_REMOTE_USEINET_ENV_VAR_NAME , "true" );
            }
            final ArgumentListBuilder cmdLine = buildMavenAgentCmdLine( listener,acceptor.getPort());
            String[] cmds = cmdLine.toCommandArray();
            final Proc proc = launcher.launch().cmds(cmds).envs(envVars).stdout(mca).pwd(workDir).start();

            Connection con;
            try {
                con = acceptor.accept();
            } catch (SocketTimeoutException e) {
                // failed to connect. Is the process dead?
                // if so, the error should have been provided by the launcher already.
                // so abort gracefully without a stack trace.
                if(!proc.isAlive())
                    throw new AbortException("Failed to launch Maven. Exit code = "+proc.join());
                throw e;
            }

            return new NewProcess(
                Channels.forProcess("Channel to Maven "+ Arrays.toString(cmds),
                    Computer.threadPoolForRemoting, new BufferedInputStream(con.in), new BufferedOutputStream(con.out),
                    listener.getLogger(), proc),
                proc);
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

        boolean isMaster = getCurrentNode()== Jenkins.getInstance();
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
        args.add(getMavenAgentClassPath(mvn,isMaster,slaveRoot,listener));
        args.add(getMainClassName());

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
        args.add(getMavenInterceptorClassPath(mvn,isMaster,slaveRoot));

        // TCP/IP port to establish the remoting infrastructure
        args.add(tcpPort);
        
        String interceptorOverride = getMavenInterceptorOverride(mvn,isMaster,slaveRoot);
        if (interceptorOverride!=null) {
            args.add(interceptorOverride);
        }

        return args;
    }
    
    /**
     * Returns the classpath string for the maven-agent jar including classworlds
     */
    protected abstract String getMavenAgentClassPath(MavenInstallation mvn,boolean isMaster,FilePath slaveRoot,BuildListener listener) throws IOException, InterruptedException;
    
    /**
     * Returns the classpath string for the maven-interceptor jar
     */
    protected abstract String getMavenInterceptorClassPath(MavenInstallation mvn,boolean isMaster,FilePath slaveRoot) throws IOException, InterruptedException;
    
    /**
     * For Maven 2.1.x - 2.2.x we need an additional jar which overrides some classes in the other interceptor jar. 
     */
    protected abstract String getMavenInterceptorOverride(MavenInstallation mvn,boolean isMaster,FilePath slaveRoot) throws IOException, InterruptedException;
    
    /**
     * Returns the name of the Maven main class.
     */
    protected abstract String getMainClassName();

    public String getMavenOpts() {
        if( this.mavenOpts != null )
            return addRunHeadLessOption(this.mavenOpts);

        String mavenOpts = mms.getMavenOpts();

        if ((mavenOpts==null) || (mavenOpts.trim().length()==0)) {
            Node n = getCurrentNode();
            if (n!=null) {
                try {
                    String localMavenOpts = n.toComputer().getEnvironment().get("MAVEN_OPTS");
                    
                    if ((localMavenOpts!=null) && (localMavenOpts.trim().length()>0)) {
                        mavenOpts = localMavenOpts;
                    }
                } catch (IOException e) {
                } catch (InterruptedException e) {
                    // Don't do anything - this just means the slave isn't running, so we
                    // don't want to use its MAVEN_OPTS anyway.
                }

            }
        }

        mavenOpts = addRunHeadLessOption(mavenOpts);

        return envVars.expand(mavenOpts);
    }

    protected String addRunHeadLessOption(String mavenOpts) {

        if (mms.runHeadless()) {
            // Configure headless process
            if (mavenOpts == null) {
                mavenOpts = "-Djava.awt.headless=true";
            } else {
                mavenOpts += " -Djava.awt.headless=true";
            }
        } else {
            if (Platform.isDarwin()) {
                // Would be cool to replace the generic Java icon with jenkins logo, but requires
                // the file absolute path to be available on slave *before* the process run on it :-/
                // Maybe we could enforce this from the DMG installer on OSX
                // TODO mavenOpts += " -Xdock:name=Jenkins -Xdock:icon=jenkins.png";
            }
        }
        return mavenOpts;
    }


    public MavenInstallation getMavenInstallation(TaskListener log) throws IOException, InterruptedException {
        MavenInstallation mi = mms.getMaven();
        if (mi != null) mi = mi.forNode(getCurrentNode(), log).forEnvironment(envVars);
        return mi;

    }

    public JDK getJava(TaskListener log) throws IOException, InterruptedException {
        JDK jdk = mms.getJDK();
        if (jdk != null) jdk = jdk.forNode(getCurrentNode(), log).forEnvironment(envVars);
        return jdk;
    }

    
    protected static final class GetRemotingJar implements Callable<String,IOException> {
        private static final long serialVersionUID = 6022357183425911351L;

        public String call() throws IOException {
            return Which.jarFile(hudson.remoting.Launcher.class).getPath();
        }
    }

    /**
     * Returns the current {@link Node} on which we are buildling.
     */
    protected Node getCurrentNode() {
        return Executor.currentExecutor().getOwner().getNode();
    }
    

    protected MavenModuleSet getMavenModuleSet() {
        return mms;
    }

    protected Launcher getLauncher() {
        return launcher;
    }

    protected EnvVars getEnvVars() {
        return envVars;
    }

    public static boolean mavenRemoteUseInet = Boolean.getBoolean("maven.remote.useinet");

    public static final String MAVEN_REMOTE_USEINET_ENV_VAR_NAME = "MAVEN_REMOTE_USEINET";
    
    /**
     * If true, launch Maven with YJP offline profiler agent.
     */
    public static boolean yjp = Boolean.getBoolean("hudson.maven.yjp");
    
    /**
     * If not 0, launch Maven with a debugger port.
     */
    public static int debugPort;
    
    static {
        String port = System.getProperty("hudson.maven.debugPort");
        if(port!=null)
            debugPort = Integer.parseInt(port);
    }
}
