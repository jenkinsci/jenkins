/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Martin Eigenbrodt, Stephen Connolly, Tom Huybrechts
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
import hudson.remoting.VirtualChannel;
import hudson.slaves.CommandLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.tasks.DynamicLabeler;
import hudson.tasks.LabelFinder;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;
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
    
	private /*almost final*/ DescribableList<NodeProperty<?>,NodePropertyDescriptor> nodeProperties = new DescribableList<NodeProperty<?>,NodePropertyDescriptor>(Hudson.getInstance());

    /**
     * Lazily computed set of labels from {@link #label}.
     */
    private transient volatile Set<Label> labels;

    private transient volatile Set<Label> dynamicLabels;
    private transient volatile int dynamicLabelsInstanceHash;

    @DataBoundConstructor
    public Slave(String name, String nodeDescription, String remoteFS, String numExecutors,
                 Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
        this(name,nodeDescription,remoteFS,Util.tryParseNumber(numExecutors, 1).intValue(),mode,labelString,launcher,retentionStrategy, nodeProperties);
    }

    @Deprecated
    public Slave(String name, String nodeDescription, String remoteFS, int numExecutors,
            Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy) throws FormException, IOException {
    	this(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, new ArrayList());
    }
    
    public Slave(String name, String nodeDescription, String remoteFS, int numExecutors,
                 Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
        this.name = name;
        this.description = nodeDescription;
        this.numExecutors = numExecutors;
        this.mode = mode;
        this.remoteFS = remoteFS;
        this.label = Util.fixNull(labelString).trim();
        this.launcher = launcher;
        this.retentionStrategy = retentionStrategy;
        getAssignedLabels();    // compute labels now
        
        this.nodeProperties.replaceBy(nodeProperties);

        if (name.equals(""))
            throw new FormException(Messages.Slave_InvalidConfig_NoName(), null);

        // this prevents the config from being saved when slaves are offline.
        // on a large deployment with a lot of slaves, some slaves are bound to be offline,
        // so this check is harmful.
        //if (!localFS.exists())
        //    throw new FormException("Invalid slave configuration for " + name + ". No such directory exists: " + localFS, null);

//        if (remoteFS.equals(""))
//            throw new FormException(Messages.Slave_InvalidConfig_NoRemoteDir(name), null);

        if (this.numExecutors<=0)
            throw new FormException(Messages.Slave_InvalidConfig_Executors(name), null);
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

    public Set<Label> getAssignedLabels() {
        // todo refactor to make dynamic labels a bit less hacky
        if(labels==null || isChangedDynamicLabels()) {
            Set<Label> r = Label.parse(getLabelString());
            r.add(getSelfLabel());
            r.addAll(getDynamicLabels());
            this.labels = Collections.unmodifiableSet(r);
        }
        return labels;
    }

    /**
     * Check if we should rebuild the list of dynamic labels.
     * @todo make less hacky
     * @return
     */
    private boolean isChangedDynamicLabels() {
        Computer comp = getComputer();
        if (comp == null) {
            return dynamicLabelsInstanceHash != 0;
        } else {
            if (dynamicLabelsInstanceHash == comp.hashCode()) {
                return false;
            }
            dynamicLabels = null; // force a re-calc
            return true;
        }
    }

    /**
     * Returns the possibly empty set of labels that it has been determined as supported by this node.
     *
     * @todo make less hacky
     * @see hudson.tasks.LabelFinder
     *
     * @return
     *      never null.
     */
    public Set<Label> getDynamicLabels() {
        // another thread may preempt and set dynamicLabels field to null,
        // so a care needs to be taken to avoid race conditions under all circumstances.
        Set<Label> labels = dynamicLabels;
        if (labels != null)     return labels;

        synchronized (this) {
            labels = dynamicLabels;
            if (labels != null)     return labels;

            dynamicLabels = labels = new HashSet<Label>();
            Computer computer = getComputer();
            VirtualChannel channel;
            if (computer != null && (channel = computer.getChannel()) != null) {
                dynamicLabelsInstanceHash = computer.hashCode();
                for (DynamicLabeler labeler : LabelFinder.LABELERS) {
                    for (String label : labeler.findLabels(channel)) {
                        labels.add(Hudson.getInstance().getLabel(label));
                    }
                }
            } else {
                dynamicLabelsInstanceHash = 0;
            }

            return labels;
        }
    }

    public ClockDifference getClockDifference() throws IOException, InterruptedException {
        VirtualChannel channel = getComputer().getChannel();
        if(channel==null)
            throw new IOException(getNodeName()+" is offline");

        long startTime = System.currentTimeMillis();
        long slaveTime = channel.call(new GetSystemTime());
        long endTime = System.currentTimeMillis();

        return new ClockDifference((startTime+endTime)/2 - slaveTime);
    }

    public Computer createComputer() {
        return new SlaveComputer(this);
    }

    public FilePath getWorkspaceFor(TopLevelItem item) {
        FilePath r = getWorkspaceRoot();
        if(r==null)     return null;    // offline
        return r.child(item.getName());
    }

    public FilePath getRootPath() {
        return createPath(remoteFS);
    }

    /**
     * Root directory on this slave where all the job workspaces are laid out.
     * @return
     *      null if not connected.
     */
    public FilePath getWorkspaceRoot() {
        FilePath r = getRootPath();
        if(r==null) return null;
        return r.child("workspace");
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
            URLConnection con = connect();
            // since we end up redirecting users to jnlpJars/foo.jar/, set the content disposition
            // so that browsers can download them in the right file name.
            rsp.setHeader("Content-Disposition", "inline; filename=" + fileName);
            InputStream in = con.getInputStream();
            rsp.serveFile(req, in, con.getLastModified(), con.getContentLength(), "*.jar" );
            in.close();
        }

        private URLConnection connect() throws IOException {
            URL res = getURL();
            return res.openConnection();
        }

        public URL getURL() throws MalformedURLException {
            URL res = Hudson.getInstance().servletContext.getResource("/WEB-INF/" + fileName);
            if(res==null) {
                // during the development this path doesn't have the files.
                res = new URL(new File(".").getAbsoluteFile().toURL(),"target/generated-resources/WEB-INF/"+fileName);
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

    public Launcher createLauncher(TaskListener listener) {
        SlaveComputer c = getComputer();
        return new RemoteLauncher(listener, c.getChannel(), c.isUnix()).decorateFor(this);
    }

    /**
     * Gets the corresponding computer object.
     */
    public SlaveComputer getComputer() {
        return (SlaveComputer)Hudson.getInstance().getComputer(this);
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
        if (launcher == null) {
            launcher = (agentCommand == null || agentCommand.trim().length() == 0)
                    ? new JNLPLauncher()
                    : new CommandLauncher(agentCommand);
        }
        if(nodeProperties==null)
            nodeProperties = new DescribableList<NodeProperty<?>,NodePropertyDescriptor>(Hudson.getInstance());
        return this;
    }

    public SlaveDescriptor getDescriptor() {
        Descriptor d = Hudson.getInstance().getDescriptor(getClass());
        if (d instanceof SlaveDescriptor)
            return (SlaveDescriptor) d;
        if (d==null)
            throw new IllegalStateException(getClass()+" doesn't have a descriptor");
        throw new IllegalStateException(d.getClass()+" needs to extend from SlaveDescriptor");
    }

    public static abstract class SlaveDescriptor extends NodeDescriptor {
        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        /**
         * Performs syntactical check on the remote FS for slaves.
         */
        public FormValidation doCheckRemoteFs(@QueryParameter String value) throws IOException, ServletException {
            if(Util.fixEmptyAndTrim(value)==null)
                return FormValidation.error("Remote directory is mandatory");

            if(value.startsWith("\\\\") || value.startsWith("/net/"))
                return FormValidation.warning("Are you sure you want to use network mounted file system for FS root? " +
                        "Note that this directory needs not be visible to the master.");

            return FormValidation.ok();
        }
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
    /**
     * Command line to launch the agent, like
     * "ssh myslave java -jar /path/to/hudson-remoting.jar"
     */
    private transient String agentCommand;

    /**
     * Obtains the system clock.
     */
    private static final class GetSystemTime implements Callable<Long,RuntimeException> {
        public Long call() {
            return System.currentTimeMillis();
        }

        private static final long serialVersionUID = 1L;
    }
}
