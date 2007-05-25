package hudson.model;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.RemoteLauncher;
import hudson.Util;
import hudson.maven.agent.Main;
import hudson.maven.agent.PluginManagerInterceptor;
import hudson.model.Descriptor.FormException;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Channel.Listener;
import hudson.remoting.VirtualChannel;
import hudson.remoting.Which;
import hudson.util.NullStream;
import hudson.util.RingBufferLogHandler;
import hudson.util.StreamCopyThread;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Information about a Hudson slave node.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Slave implements Node, Serializable {
    /**
     * PluginName of this slave node.
     */
    protected final String name;

    /**
     * Description of this node.
     */
    private final String description;

    /**
     * Path to the root of the workspace
     * from the view point of this node, such as "/hudson"
     */
    protected final String remoteFS;

    /**
     * Number of executors of this node.
     */
    private int numExecutors = 2;

    /**
     * Job allocation strategy.
     */
    private Mode mode;

    /**
     * JNLP Security mode
     */
    private JNLPSecurityMode jnlpSecurity;

    /**
     * Command line to launch the agent, like
     * "ssh myslave java -jar /path/to/hudson-remoting.jar"
     */
    private String agentCommand;

    /**
     * Whitespace-separated labels.
     */
    private String label="";

    /**
     * Lazily computed set of labels from {@link #label}.
     */
    private transient volatile Set<Label> labels;

    /**
     * @stapler-constructor
     */
    public Slave(String name, String description, String command, String remoteFS, int numExecutors, Mode mode,
                 String label, JNLPSecurityMode jnlpSecurity) throws FormException {
        this.name = name;
        this.description = description;
        this.numExecutors = numExecutors;
        this.mode = mode;
        this.agentCommand = command;
        this.remoteFS = remoteFS;
        this.jnlpSecurity = jnlpSecurity;
        this.label = Util.fixNull(label).trim();
        getAssignedLabels();    // compute labels now

        if (name.equals(""))
            throw new FormException("Invalid slave configuration. PluginName is empty", null);

        // this prevents the config from being saved when slaves are offline.
        // on a large deployment with a lot of slaves, some slaves are bound to be offline,
        // so this check is harmful.
        //if (!localFS.exists())
        //    throw new FormException("Invalid slave configuration for " + name + ". No such directory exists: " + localFS, null);
        if (remoteFS.equals(""))
            throw new FormException("Invalid slave configuration for " + name + ". No remote directory given", null);
    }

    public String getCommand() {
        return agentCommand;
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    public String getNodeName() {
        return name;
    }

    public String getNodeDescription() {
        return description;
    }

    /**
     * Gets the root directory of the Hudson workspace on this slave.
     */
    public FilePath getFilePath() {
        return new FilePath(getComputer().getChannel(),remoteFS);
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public Mode getMode() {
        return mode;
    }

    public JNLPSecurityMode getJnlpSecurity() {
        return jnlpSecurity;
    }

    public String getLabelString() {
        return Util.fixNull(label).trim();
    }
    public Set<Label> getAssignedLabels() {
        if(labels==null) {
            Set<Label> r = new HashSet<Label>();
            String ls = getLabelString();
            if(ls.length()>0) {
                for( String l : ls.split(" +")) {
                    r.add(Hudson.getInstance().getLabel(l));
                }
            }
            r.add(getSelfLabel());
            this.labels = Collections.unmodifiableSet(r);
        }
        return labels;
    }


    public Label getSelfLabel() {
        return Hudson.getInstance().getLabel(name);
    }

    /**
     * Estimates the clock difference with this slave.
     *
     * @return
     *      difference in milli-seconds.
     *      a positive value indicates that the master is ahead of the slave,
     *      and negative value indicates otherwise.
     */
    public long getClockDifference() throws IOException {
        VirtualChannel channel = getComputer().getChannel();
        if(channel==null)   return 0;   // can't check

        try {
            long startTime = System.currentTimeMillis();
            long slaveTime = channel.call(new Callable<Long,RuntimeException>() {
                public Long call() {
                    return System.currentTimeMillis();
                }
            });
            long endTime = System.currentTimeMillis();

            return (startTime+endTime)/2 - slaveTime;
        } catch (InterruptedException e) {
            return 0;   // couldn't check
        }
    }


    /**
     * Gets the clock difference in HTML string.
     */
    public String getClockDifferenceString() {
        try {
            long diff = getClockDifference();
            if(-1000<diff && diff <1000)
                return "In sync";  // clock is in sync

            long abs = Math.abs(diff);

            String s = Util.getTimeSpanString(abs);
            if(diff<0)
                s += " ahead";
            else
                s += " behind";

            if(abs>100*60) // more than a minute difference
                s = "<span class='error'>"+s+"</span>";

            return s;
        } catch (IOException e) {
            return "<span class='error'>Unable to check</span>";
        }
    }

    public Computer createComputer() {
        return new ComputerImpl(this);
    }

    public FilePath getWorkspaceFor(TopLevelItem item) {
        return getWorkspaceRoot().child(item.getName());
    }

    /**
     * Root directory on this slave where all the job workspaces are laid out.
     */
    public FilePath getWorkspaceRoot() {
        return getFilePath().child("workspace");
    }

    public static final class ComputerImpl extends Computer {
        private volatile Channel channel;

        /**
         * This is where the log from the remote agent goes.
         */
        private File getLogFile() {
            return new File(Hudson.getInstance().getRootDir(),"slave-"+nodeName+".log");
        }

        private ComputerImpl(Slave slave) {
            super(slave);
        }

        public Slave getNode() {
            return (Slave)super.getNode();
        }

        @Override
        public boolean isJnlpAgent() {
            return getNode().getCommand().length()==0;
        }


        /**
         * Returns true if this computer is needs to be launched via JNLP. That is if the Launch slave agent link should be
         * visible.
         *
         * @return true if and only if the JNLP link should be shown.
         */
        @Override
        public boolean isJnlpAgentLaunchVisible() {
            if (getNode().getJnlpSecurity().isDynammicPool()) {
                return isJnlpAgent();
            }
            return super.isJnlpAgentLaunchVisible();
        }

        /**
         * Returns true if the JNLP link should be restricted to authenticated in users.
         *
         * @return true if and only if the JNLP link should be restricted to authenticated users.
         */
        @Override
        public boolean isJnlpAgentLaunchAdminOnly() {
            return getNode().getJnlpSecurity().isEnforceSecurity();
        }

        /**
         * Returns true if the JNLP link should be visible from the main page.
         *
         * @return true if and only if the JNLP link should be availible from the main page.
         */
        @Override
        public boolean isJnlpAgentLaunchPublic() {
            if (getNode().getJnlpSecurity().isPublicLaunch()) {
                return isJnlpAgent();
            }
            return super.isJnlpAgentLaunchPublic();    //To change body of overridden methods use File | Settings | File Templates.
        }

        /**
         * Launches a remote agent.
         */
        private void launch(final Slave slave) {
            closeChannel();

            final OutputStream launchLog = openLogFile();

            if(slave.agentCommand.length()>0) {
                // launch the slave agent asynchronously
                threadPoolForRemoting.execute(new Runnable() {
                    // TODO: do this only for nodes that are so configured.
                    // TODO: support passive connection via JNLP
                    public void run() {
                        final StreamTaskListener listener = new StreamTaskListener(launchLog);
                        try {
                            listener.getLogger().println("Launching slave agent");
                            listener.getLogger().println("$ "+slave.agentCommand);
                            final Process proc = Runtime.getRuntime().exec(slave.agentCommand);

                            // capture error information from stderr. this will terminate itself
                            // when the process is killed.
                            new StreamCopyThread("stderr copier for remote agent on "+slave.getNodeName(),
                                proc.getErrorStream(), launchLog).start();

                            setChannel(proc.getInputStream(),proc.getOutputStream(),launchLog,new Listener() {
                                public void onClosed(Channel channel, IOException cause) {
                                    if(cause!=null)
                                        cause.printStackTrace(listener.error("slave agent was terminated"));
                                    proc.destroy();
                                }
                            });

                            logger.info("slave agent launched for "+slave.getNodeName());

                        } catch (InterruptedException e) {
                            e.printStackTrace(listener.error("aborted"));
                        } catch (IOException e) {
                            Util.displayIOException(e,listener);

                            String msg = Util.getWin32ErrorMessage(e);
                            if(msg==null)   msg="";
                            else            msg=" : "+msg;
                            msg = "Unable to launch the slave agent for " + slave.getNodeName() + msg;
                            logger.log(Level.SEVERE,msg,e);
                            e.printStackTrace(listener.error(msg));
                        }
                    }
                });
            }
        }

        public OutputStream openLogFile() {
            OutputStream os;
            try {
                os = new FileOutputStream(getLogFile());
            } catch (FileNotFoundException e) {
                logger.log(Level.SEVERE, "Failed to create log file "+getLogFile(),e);
                os = new NullStream();
            }
            return os;
        }

        private final Object channelLock = new Object();

        /**
         * Creates a {@link Channel} from the given stream and sets that to this slave.
         */
        public void setChannel(InputStream in, OutputStream out, OutputStream launchLog, Listener listener) throws IOException, InterruptedException {
            synchronized(channelLock) {
                if(this.channel!=null)
                    throw new IllegalStateException("Already connected");

                Channel channel = new Channel(nodeName,threadPoolForRemoting,
                    in,out, launchLog);
                channel.addListener(new Listener() {
                    public void onClosed(Channel c,IOException cause) {
                        ComputerImpl.this.channel = null;
                    }
                });
                channel.addListener(listener);

                {// send jars that we need for our operations
                    // TODO: maybe I should generalize this kind of "post initialization" processing
                    PrintWriter log = new PrintWriter(launchLog,true);
                    FilePath dst = new FilePath(channel,getNode().getRemoteFS());
                    new FilePath(Which.jarFile(Main.class)).copyTo(dst.child("maven-agent.jar"));
                    log.println("Copied maven-agent.jar");
                    new FilePath(Which.jarFile(PluginManagerInterceptor.class)).copyTo(dst.child("maven-interceptor.jar"));
                    log.println("Copied maven-interceptor.jar");
                }

                // install log handler
                channel.call(new LogInstaller());


                // prevent others from seeing a channel that's not properly initialized yet
                this.channel = channel;
            }
            Hudson.getInstance().getQueue().scheduleMaintenance();
        }

        @Override
        public VirtualChannel getChannel() {
            return channel;
        }

        public List<LogRecord> getLogRecords() throws IOException, InterruptedException {
            if(channel==null)
                return Collections.emptyList();
            else
                return channel.call(new Callable<List<LogRecord>,RuntimeException>() {
                    public List<LogRecord> call() {
                        return new ArrayList<LogRecord>(SLAVE_LOG_HANDLER.getView());
                    }
                });
        }

        public void doDoDisconnect(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            if(!Hudson.adminCheck(req,rsp))
                return;
            closeChannel();
            rsp.sendRedirect(".");
        }

        public void doLaunchSlaveAgent(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            if(channel!=null) {
                rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            launch(getNode());

            // TODO: would be nice to redirect the user to "launching..." wait page,
            // then spend a few seconds there and poll for the completion periodically.
            rsp.sendRedirect("log");
        }

        /**
         * Gets the string representation of the slave log.
         */
        public String getLog() throws IOException {
            return Util.loadFile(getLogFile());
        }

        /**
         * Handles incremental log.
         */
        public void doProgressiveLog( StaplerRequest req, StaplerResponse rsp) throws IOException {
            new LargeText(getLogFile(),false).doProgressText(req,rsp);
        }

        /**
         * Serves jar files for JNLP slave agents.
         */
        public JnlpJar getJnlpJars(String fileName) {
            return new JnlpJar(fileName);
        }

        @Override
        protected void kill() {
            super.kill();
            closeChannel();
        }

        private void closeChannel() {
            Channel c = channel;
            channel = null;
            if(c!=null)
                try {
                    c.close();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Failed to terminate channel to "+getDisplayName(),e);
                }
        }

        @Override
        protected void setNode(Node node) {
            super.setNode(node);
            if(channel==null)
                // maybe the configuration was changed to relaunch the slave, so try it now.
                launch((Slave)node);
        }

        private static final Logger logger = Logger.getLogger(ComputerImpl.class.getName());
    }

    /**
     * Web-bound object used to serve jar files for JNLP.
     */
    public static final class JnlpJar {
        private final String fileName;

        public JnlpJar(String fileName) {
            this.fileName = fileName;
        }

        public void doIndex( StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            URL res = req.getServletContext().getResource("/WEB-INF/" + fileName);
            if(res==null) {
                // during the development this path doesn't have the files.
                res = new URL(new File(".").getAbsoluteFile().toURL(),"target/generated-resources/WEB-INF/"+fileName);
            }

            URLConnection con = res.openConnection();
            InputStream in = con.getInputStream();
            rsp.serveFile(req, in, con.getLastModified(), con.getContentLength(), "*.jar" );
            in.close();
        }

    }

    public Launcher createLauncher(TaskListener listener) {
        // Windows can handle '/' as a path separator but Unix can't,
        // so err on Unix side
        boolean isUnix = remoteFS.indexOf("\\") == -1;

        return new RemoteLauncher(listener, getComputer().getChannel(),isUnix);
    }

    /**
     * Gets th ecorresponding computer object.
     */
    public Computer getComputer() {
        return Hudson.getInstance().getComputer(getNodeName());
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Slave that = (Slave) o;

        return name.equals(that.name);
    }

    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Invoked by XStream when this object is read into memory.
     */
    private Object readResolve() {
        // convert the old format to the new one
        if(command!=null && agentCommand==null) {
            if(command.length()>0)  command += ' ';
            agentCommand = command+"java -jar ~/bin/slave.jar";
        }
        return this;
    }

    /**
     * This field is used on each slave node to record log records on the slave.
     */
    private static final RingBufferLogHandler SLAVE_LOG_HANDLER = new RingBufferLogHandler();

    private static class LogInstaller implements Callable<Void,RuntimeException> {
        public Void call() {
            // avoid double installation of the handler
            Logger logger = Logger.getLogger("hudson");
            logger.removeHandler(SLAVE_LOG_HANDLER);
            logger.addHandler(SLAVE_LOG_HANDLER);
            return null;
        }
        private static final long serialVersionUID = 1L;
    }

//
// backwrad compatibility
//
    /**
     * In Hudson < 1.69 this was used to store the local file path
     * to the remote workspace. No longer in use.
     *
     * @deprecated
     *      ... but still in use during the transition.
     */
    private File localFS;

    /**
     * In Hudson < 1.69 this was used to store the command
     * to connect to the remote machine, like "ssh myslave".
     *
     * @deprecated
     */
    private transient String command;
}
