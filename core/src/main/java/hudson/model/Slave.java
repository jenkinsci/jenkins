/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Erik Ramfelt, Martin Eigenbrodt, Stephen Connolly, Tom Huybrechts
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

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.Launcher.RemoteLauncher;
import hudson.model.Descriptor.FormException;
import hudson.remoting.Callable;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.ServletException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import jenkins.slaves.WorkspaceLocator;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Information about a Hudson slave node.
 *
 * <p>
 * Ideally this would have been in the <tt>hudson.slaves</tt> package,
 * but for compatibility reasons, it can't.
 *
 * <p>
 * TODO: move out more stuff to {@link DumbSlave}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Slave extends Node implements Serializable {
    /**
     * Name of this slave node.
     */
    protected String name;

    /**
     * Description of this node.
     */
    private final String description;

    /**
     * Absolute path to the root of the workspace
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
     * Slave availablility strategy.
     */
    private RetentionStrategy retentionStrategy;

    /**
     * The starter that will startup this slave.
     */
    private ComputerLauncher launcher;

    /**
     * Whitespace-separated labels.
     */
    private String label="";
    
    private /*almost final*/ DescribableList<NodeProperty<?>,NodePropertyDescriptor> nodeProperties = new DescribableList<NodeProperty<?>,NodePropertyDescriptor>(Jenkins.getInstance());

    /**
     * Lazily computed set of labels from {@link #label}.
     */
    private transient volatile Set<Label> labels;
    
    /**
     * Id of user which creates this slave {@link User}.
     */
    private String userId;

    public Slave(String name, String nodeDescription, String remoteFS, String numExecutors,
                 Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
        this(name,nodeDescription,remoteFS,Util.tryParseNumber(numExecutors, 1).intValue(),mode,labelString,launcher,retentionStrategy, nodeProperties);
    }

    /**
     * @deprecated since 2009-02-20.
     */
    @Deprecated
    public Slave(String name, String nodeDescription, String remoteFS, int numExecutors,
            Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy) throws FormException, IOException {
    	this(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, new ArrayList());
    }
    
    public Slave(@Nonnull String name, String nodeDescription, String remoteFS, int numExecutors,
                 Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
        this.name = name;
        this.description = nodeDescription;
        this.numExecutors = numExecutors;
        this.mode = mode;
        this.remoteFS = Util.fixNull(remoteFS).trim();
        this.label = Util.fixNull(labelString).trim();
        this.launcher = launcher;
        this.retentionStrategy = retentionStrategy;
        getAssignedLabels();    // compute labels now
        
        this.nodeProperties.replaceBy(nodeProperties);
         Slave node = (Slave) Jenkins.getInstance().getNode(name);

       if(node!=null){
            this.userId= node.getUserId(); //slave has already existed
        }
       else{
            User user = User.current();
            userId = user!=null ? user.getId() : "anonymous";     
        }
        if (name.equals(""))
            throw new FormException(Messages.Slave_InvalidConfig_NoName(), null);

//        if (remoteFS.equals(""))
//            throw new FormException(Messages.Slave_InvalidConfig_NoRemoteDir(name), null);

        if (this.numExecutors<=0)
            throw new FormException(Messages.Slave_InvalidConfig_Executors(name), null);
    }
    
    /**
     * Return id of user which created this slave
     * 
     * @return id of user
     */
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId){
        this.userId = userId;
    }
    
    public ComputerLauncher getLauncher() {
        return launcher == null ? new JNLPLauncher() : launcher;
    }

    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    public String getNodeName() {
        return name;
    }

    public void setNodeName(String name) {
        this.name = name; 
    }

    public String getNodeDescription() {
        return description;
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
        assert nodeProperties != null;
    	return nodeProperties;
    }
    
    public RetentionStrategy getRetentionStrategy() {
        return retentionStrategy == null ? RetentionStrategy.Always.INSTANCE : retentionStrategy;
    }

    public void setRetentionStrategy(RetentionStrategy availabilityStrategy) {
        this.retentionStrategy = availabilityStrategy;
    }

    public String getLabelString() {
        return Util.fixNull(label).trim();
    }

    @Override
    public void setLabelString(String labelString) throws IOException {
        this.label = Util.fixNull(labelString).trim();
        // Compute labels now.
        getAssignedLabels();
    }

    @Override
    public Callable<ClockDifference,IOException> getClockDifferenceCallable() {
        return new GetClockDifference1();
    }

    public Computer createComputer() {
        return new SlaveComputer(this);
    }

    public FilePath getWorkspaceFor(TopLevelItem item) {
        for (WorkspaceLocator l : WorkspaceLocator.all()) {
            FilePath workspace = l.locate(item, this);
            if (workspace != null) {
                return workspace;
            }
        }
        
        FilePath r = getWorkspaceRoot();
        if(r==null)     return null;    // offline
        return r.child(item.getFullName());
    }

    public FilePath getRootPath() {
        return createPath(remoteFS);
    }

    /**
     * Root directory on this slave where all the job workspaces are laid out.
     * @return
     *      null if not connected.
     */
    public @CheckForNull FilePath getWorkspaceRoot() {
        FilePath r = getRootPath();
        if(r==null) return null;
        return r.child(WORKSPACE_ROOT);
    }

    /**
     * Web-bound object used to serve jar files for JNLP.
     */
    public static final class JnlpJar implements HttpResponse {
        private final String fileName;

        public JnlpJar(String fileName) {
            this.fileName = fileName;
        }

        public void doIndex( StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            URLConnection con = connect();
            // since we end up redirecting users to jnlpJars/foo.jar/, set the content disposition
            // so that browsers can download them in the right file name.
            // see http://support.microsoft.com/kb/260519 and http://www.boutell.com/newfaq/creating/forcedownload.html
            rsp.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            InputStream in = con.getInputStream();
            rsp.serveFile(req, in, con.getLastModified(), con.getContentLength(), "*.jar" );
            in.close();
        }

        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            doIndex(req,rsp);
        }

        private URLConnection connect() throws IOException {
            URL res = getURL();
            return res.openConnection();
        }

        public URL getURL() throws MalformedURLException {
            String name = fileName;
            
            // Prevent the access to war contents & prevent the folder escaping (SECURITY-195)
            if (!ALLOWED_JNLPJARS_FILES.contains(name)) {
                throw new MalformedURLException("The specified file path " + fileName + " is not allowed due to security reasons");
            }
            
            if (name.equals("hudson-cli.jar"))  {
                name="jenkins-cli.jar";
            }
            
            URL res = Jenkins.getInstance().servletContext.getResource("/WEB-INF/" + name);
            if(res==null) {
                // during the development this path doesn't have the files.
                res = new URL(new File(".").getAbsoluteFile().toURI().toURL(),"target/jenkins/WEB-INF/"+name);
            }
            return res;
        }

        public byte[] readFully() throws IOException {
            InputStream in = connect().getInputStream();
            try {
                return IOUtils.toByteArray(in);
            } finally {
                in.close();
            }
        }

    }

    /**
     * Creates a launcher for the slave.
     *
     * @return
     *      If there is no computer it will return a {@link hudson.Launcher.DummyLauncher}, otherwise it
     *      will return a {@link hudson.Launcher.RemoteLauncher} instead.
     */
    public Launcher createLauncher(TaskListener listener) {
        SlaveComputer c = getComputer();
        if (c == null) {
            listener.error("Issue with creating launcher for slave " + name + ".");
            return new Launcher.DummyLauncher(listener);
        } else {
            return new RemoteLauncher(listener, c.getChannel(), c.isUnix()).decorateFor(this);
        }
    }

    /**
     * Gets the corresponding computer object.
     */
    public SlaveComputer getComputer() {
        return (SlaveComputer)toComputer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Slave that = (Slave) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Invoked by XStream when this object is read into memory.
     */
    protected Object readResolve() {
        // convert the old format to the new one
        if (launcher == null) {
            launcher = (agentCommand == null || agentCommand.trim().length() == 0)
                    ? new JNLPLauncher()
                    : new CommandLauncher(agentCommand);
        }
        if(nodeProperties==null)
            nodeProperties = new DescribableList<NodeProperty<?>,NodePropertyDescriptor>(Jenkins.getInstance());
        return this;
    }

    public SlaveDescriptor getDescriptor() {
        Descriptor d = Jenkins.getInstance().getDescriptorOrDie(getClass());
        if (d instanceof SlaveDescriptor)
            return (SlaveDescriptor) d;
        throw new IllegalStateException(d.getClass()+" needs to extend from SlaveDescriptor");
    }

    public static abstract class SlaveDescriptor extends NodeDescriptor {
        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        /**
         * Performs syntactical check on the remote FS for slaves.
         */
        public FormValidation doCheckRemoteFS(@QueryParameter String value) throws IOException, ServletException {
            if(Util.fixEmptyAndTrim(value)==null)
                return FormValidation.error(Messages.Slave_Remote_Director_Mandatory());

            if(value.startsWith("\\\\") || value.startsWith("/net/"))
                return FormValidation.warning(Messages.Slave_Network_Mounted_File_System_Warning());

            if (!value.contains("\\") && !value.startsWith("/")) {
                // Unix-looking path that doesn't start with '/'
                // TODO: detect Windows-looking relative path
                return FormValidation.error(Messages.Slave_the_remote_root_must_be_an_absolute_path());
            }

            return FormValidation.ok();
        }
    }


//
// backward compatibility
//
    /**
     * Command line to launch the agent, like
     * "ssh myslave java -jar /path/to/hudson-remoting.jar"
     * @deprecated in 1.216
     */
    private transient String agentCommand;

    /**
     * Obtains the clock difference between this side and that side of the channel.
     *
     * <p>
     * This is a hack to wrap the whole thing into a simple {@link Callable}.
     *
     * <ol>
     *     <li>When the callable is sent to remote, we capture the time (on this side) in {@link GetClockDifference2#startTime}
     *     <li>When the other side receives the callable it is {@link GetClockDifference2}.
     *     <li>We capture the time on the other side and {@link GetClockDifference3} gets sent from the other side
     *     <li>When it's read on this side as a return value, it morphs itself into {@link ClockDifference}.
     * </ol>
     */
    private static final class GetClockDifference1 extends MasterToSlaveCallable<ClockDifference,IOException> {
        public ClockDifference call() {
            // this method must be being invoked locally, which means the clock is in sync
            return new ClockDifference(0);
        }

        private Object writeReplace() {
            return new GetClockDifference2();
        }

        private static final long serialVersionUID = 1L;
    }

    private static final class GetClockDifference2 extends MasterToSlaveCallable<GetClockDifference3,IOException> {
        /**
         * Capture the time on the master when this object is sent to remote, which is when
         * {@link GetClockDifference1#writeReplace()} is run.
         */
        private final long startTime = System.currentTimeMillis();

        public GetClockDifference3 call() {
            return new GetClockDifference3(startTime);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final class GetClockDifference3 implements Serializable {
        private final long remoteTime = System.currentTimeMillis();
        private final long startTime;

        public GetClockDifference3(long startTime) {
            this.startTime = startTime;
        }

        private Object readResolve() {
            long endTime = System.currentTimeMillis();
            return new ClockDifference((startTime + endTime)/2-remoteTime);
        }
    }

    /**
     * Determines the workspace root file name for those who really really need the shortest possible path name.
     */
    private static final String WORKSPACE_ROOT = System.getProperty(Slave.class.getName()+".workspaceRoot","workspace");

    /**
     * Provides a collection of file names, which are accessible via /jnlpJars link.
     */
    private static final Set<String> ALLOWED_JNLPJARS_FILES = new TreeSet<String>
        (Arrays.asList("slave.jar", "remoting.jar", "jenkins-cli.jar", "hudson-cli.jar"));
}
