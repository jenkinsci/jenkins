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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.RemoteLauncher;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.cli.CLI;
import hudson.model.Descriptor.FormException;
import hudson.model.labels.LabelAtom;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Which;
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
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import jenkins.slaves.WorkspaceLocator;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Information about a Hudson agent node.
 *
 * <p>
 * Ideally this would have been in the {@code hudson.slaves} package,
 * but for compatibility reasons, it can't.
 *
 * <p>
 * TODO: move out more stuff to {@link DumbSlave}.
 *
 * On February, 2016 a general renaming was done internally: the "slave" term was replaced by
 * "Agent". This change was applied in: UI labels/HTML pages, javadocs and log messages.
 * Java classes, fields, methods, etc were not renamed to avoid compatibility issues.
 * See <a href="https://www.jenkins.io/issue/27268">JENKINS-27268</a>.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Slave extends Node implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(Slave.class.getName());

    /**
     * Name of this agent node.
     */
    protected String name;

    /**
     * Description of this node.
     */
    private String description;

    /**
     * Path to the root of the workspace from the view point of this node, such as "/hudson", this need not
     * be absolute provided that the launcher establishes a consistent working directory, such as "./.jenkins-agent"
     * when used with an SSH launcher.
     *
     * NOTE: if the administrator is using a relative path they are responsible for ensuring that the launcher used
     * provides a consistent working directory
     */
    protected final String remoteFS;

    /**
     * Number of executors of this node.
     */
    private int numExecutors = 1;

    /**
     * Job allocation strategy.
     */
    private Mode mode = Mode.NORMAL;

    /**
     * Agent availability strategy.
     */
    private RetentionStrategy retentionStrategy;

    /**
     * The starter that will startup this agent.
     */
    private ComputerLauncher launcher;

    /**
     * Whitespace-separated labels.
     */
    private String label = "";

    private /*almost final*/ DescribableList<NodeProperty<?>, NodePropertyDescriptor> nodeProperties =
            new DescribableList<>(this);

    /**
     * @deprecated Removed with no replacement.
     */
    @Deprecated
    private transient String userId;

    /**
     * Use {@link #Slave(String, String, ComputerLauncher)} and set the rest through setters.
     * @deprecated since 2.184
     */
    @Deprecated
    protected Slave(String name, String nodeDescription, String remoteFS, String numExecutors,
                 Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
        this(name, nodeDescription, remoteFS, Util.tryParseNumber(numExecutors, 1).intValue(), mode, labelString, launcher, retentionStrategy, nodeProperties);
    }

    /**
     * @deprecated since 2009-02-20.
     */
    @Deprecated
    protected Slave(String name, String nodeDescription, String remoteFS, int numExecutors,
            Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy) throws FormException, IOException {
        this(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, new ArrayList());
    }

    protected Slave(@NonNull String name, String remoteFS, ComputerLauncher launcher) throws FormException, IOException {
        this.name = name;
        this.remoteFS = remoteFS;
        this.launcher = launcher;
        this.labelAtomSet = Collections.unmodifiableSet(Label.parse(label));
    }

    /**
     * @deprecated as of 2.2
     *      Use {@link #Slave(String, String, ComputerLauncher)} and set the rest through setters.
     */
    @Deprecated
    protected Slave(@NonNull String name, String nodeDescription, String remoteFS, int numExecutors,
                 Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
        this.name = name;
        this.description = nodeDescription;
        this.numExecutors = numExecutors;
        this.mode = mode;
        this.remoteFS = Util.fixNull(remoteFS).trim();
        _setLabelString(labelString);
        this.launcher = launcher;
        this.retentionStrategy = retentionStrategy;
        getAssignedLabels();    // compute labels now

        this.nodeProperties.replaceBy(nodeProperties);

        if (name.isEmpty())
            throw new FormException(Messages.Slave_InvalidConfig_NoName(), null);

        if (this.numExecutors <= 0)
            throw new FormException(Messages.Slave_InvalidConfig_Executors(name), null);
    }

    /**
     * Return id of user which created this agent
     *
     * @return id of user
     *
     * @deprecated Removed with no replacement
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.220")
    public String getUserId() {
        return userId;
    }

    /**
     * This method no longer does anything.
     *
     * @deprecated Removed with no replacement
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("2.220")
    public void setUserId(String userId){
    }

    public ComputerLauncher getLauncher() {
        if (launcher == null && agentCommand != null && !agentCommand.isEmpty()) {
            try {
                launcher = (ComputerLauncher) Jenkins.get().getPluginManager().uberClassLoader.loadClass("hudson.slaves.CommandLauncher").getConstructor(String.class, EnvVars.class).newInstance(agentCommand, null);
                agentCommand = null;
                save();
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, "could not update historical agentCommand setting to CommandLauncher", x);
            }
        }
        return launcher == null ? new JNLPLauncher() : launcher;
    }

    /**
     * @deprecated In most cases, you should not call this method directly, but {@link Jenkins#updateNode(Node)} instead.
     */
    @Override
    @Deprecated
    public void save() throws IOException {
        super.save();
    }

    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    @NonNull
    @Override
    public String getNodeName() {
        return name;
    }

    @Override public String toString() {
        return getClass().getName() + "[" + name + "]";
    }

    @Override
    public void setNodeName(String name) {
        this.name = name;
    }

    @DataBoundSetter
    public void setNodeDescription(String value) {
        this.description = value;
    }

    @Override
    public String getNodeDescription() {
        return description;
    }

    @Override
    public int getNumExecutors() {
        return numExecutors;
    }

    @DataBoundSetter
    public void setNumExecutors(int n) {
        this.numExecutors = n;
    }

    @Override
    public Mode getMode() {
        return mode;
    }

    @DataBoundSetter
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    @NonNull
    @Override
    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
        assert nodeProperties != null;
        return nodeProperties;
    }

    @DataBoundSetter
    public void setNodeProperties(List<? extends NodeProperty<?>> properties) throws IOException {
        if (nodeProperties == null) {
            warnPlugin();
            nodeProperties = new DescribableList<>(this);
        }
        nodeProperties.replaceBy(properties);
    }

    public RetentionStrategy getRetentionStrategy() {
        return retentionStrategy == null ? RetentionStrategy.Always.INSTANCE : retentionStrategy;
    }

    @DataBoundSetter
    public void setRetentionStrategy(RetentionStrategy availabilityStrategy) {
        this.retentionStrategy = availabilityStrategy;
    }

    @Override
    public String getLabelString() {
        return Util.fixNull(label).trim();
    }

    private transient Set<LabelAtom> previouslyAssignedLabels = new HashSet<>();

    /**
     * @return the labels to be trimmed for this node. This includes current and previous labels that were applied before the last call to this method.
     */
    @Override
    @NonNull
    @Restricted(NoExternalUse.class)
    public Set<LabelAtom> drainLabelsToTrim() {
        var result = new HashSet<>(super.drainLabelsToTrim());
        synchronized (previouslyAssignedLabels) {
            result.addAll(previouslyAssignedLabels);
            previouslyAssignedLabels.clear();
        }
        return result;
    }

    @Override
    @DataBoundSetter
    public void setLabelString(String labelString) throws IOException {
        _setLabelString(labelString);
        // Compute labels now.
        getAssignedLabels();
    }

    private void _setLabelString(String labelString) {
        synchronized (this.previouslyAssignedLabels) {
            this.previouslyAssignedLabels.addAll(getAssignedLabels().stream().filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        this.label = Util.fixNull(labelString).trim();
        this.labelAtomSet = Collections.unmodifiableSet(Label.parse(label));
    }

    @CheckForNull // should be @NonNull, but we've seen plugins overriding readResolve() without calling super.
    private transient Set<LabelAtom> labelAtomSet;

    @Override
    protected Set<LabelAtom> getLabelAtomSet() {
        if (labelAtomSet == null) {
            if (!insideReadResolve.get()) {
                warnPlugin();
            }
            this.labelAtomSet = Collections.unmodifiableSet(Label.parse(label));
        }
        return labelAtomSet;
    }

    private void warnPlugin() {
        LOGGER.log(Level.WARNING, () -> getClass().getName() + " or one of its superclass overrides readResolve() without calling super implementation." +
                "Please file an issue against the plugin implementing it: " + Jenkins.get().getPluginManager().whichPlugin(getClass()));
    }

    @Override
    public Callable<ClockDifference, IOException> getClockDifferenceCallable() {
        return new GetClockDifference1();
    }

    @Override
    public Computer createComputer() {
        return new SlaveComputer(this);
    }

    @Override
    public FilePath getWorkspaceFor(TopLevelItem item) {
        for (WorkspaceLocator l : WorkspaceLocator.all()) {
            FilePath workspace = l.locate(item, this);
            if (workspace != null) {
                return workspace;
            }
        }

        FilePath r = getWorkspaceRoot();
        if (r == null)     return null;    // offline
        return r.child(item.getFullName());
    }

    @Override
    @CheckForNull
    public FilePath getRootPath() {
        final SlaveComputer computer = getComputer();
        if (computer == null) {
            // if computer is null then channel is null and thus we were going to return null anyway
            return null;
        } else {
            return createPath(Objects.toString(computer.getAbsoluteRemoteFs(), remoteFS));
        }
    }

    /**
     * Root directory on this agent where all the job workspaces are laid out.
     * @return
     *      null if not connected.
     */
    public @CheckForNull FilePath getWorkspaceRoot() {
        FilePath r = getRootPath();
        if (r == null) return null;
        return r.child(WORKSPACE_ROOT);
    }

    /**
     * Web-bound object used to serve jar files for inbound connections.
     */
    public static final class JnlpJar implements HttpResponse {
        private final String fileName;

        public JnlpJar(String fileName) {
            this.fileName = fileName;
        }

        public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
            URLConnection con = connect();
            // since we end up redirecting users to jnlpJars/foo.jar/, set the content disposition
            // so that browsers can download them in the right file name.
            // see http://support.microsoft.com/kb/260519 and http://www.boutell.com/newfaq/creating/forcedownload.html
            rsp.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            try (InputStream in = con.getInputStream()) {
                rsp.serveFile(req, in, con.getLastModified(), con.getContentLengthLong(), "*.jar");
            }
        }

        @Override
        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
            doIndex(req, rsp);
        }

        private URLConnection connect() throws IOException {
            URL res = getURL();
            return res.openConnection();
        }

        public URL getURL() throws IOException {
            String name = fileName;

            // Prevent the access to war contents & prevent the folder escaping (SECURITY-195)
            if (!ALLOWED_JNLPJARS_FILES.contains(name)) {
                throw new MalformedURLException("The specified file path " + fileName + " is not allowed due to security reasons");
            }

            Class<?> owner = null;
            if (name.equals("hudson-cli.jar") || name.equals("jenkins-cli.jar"))  {
                owner = CLI.class;
            } else if (name.equals("agent.jar") || name.equals("slave.jar") || name.equals("remoting.jar")) {
                owner = hudson.remoting.Launcher.class;
            }
            if (owner != null) {
                File jar = Which.jarFile(owner);
                if (jar.isFile()) {
                    name = "lib/" + jar.getName();
                } else {
                    URL res = findExecutableJar(jar, owner);
                    if (res != null) {
                        return res;
                    }
                }
            }

            URL res = Jenkins.get().getServletContext().getResource("/WEB-INF/" + name);
            if (res == null) {
                throw new FileNotFoundException(name); // giving up
            } else {
                LOGGER.log(Level.FINE, "found {0}", res);
            }
            return res;
        }

        /** Useful for {@code JenkinsRule.createSlave}, {@code mvn jetty:run}, etc. */
        private @CheckForNull URL findExecutableJar(File notActuallyJAR, Class<?> mainClass) throws IOException {
            if (notActuallyJAR.getName().equals("classes")) {
                File[] siblings = notActuallyJAR.getParentFile().listFiles();
                if (siblings != null) {
                    for (File actualJar : siblings) {
                        if (actualJar.getName().endsWith(".jar")) {
                            try (JarFile jf = new JarFile(actualJar, false)) {
                                Manifest mf = jf.getManifest();
                                if (mf != null && mainClass.getName().equals(mf.getMainAttributes().getValue("Main-Class"))) {
                                    LOGGER.log(Level.FINE, "found {0}", actualJar);
                                    return actualJar.toURI().toURL();
                                }
                            }
                        }
                    }
                }
            }
            return null;
        }

        public byte[] readFully() throws IOException {
            try (InputStream in = connect().getInputStream()) {
                return in.readAllBytes();
            }
        }

    }

    /**
     * Creates a launcher for the agent.
     *
     * @return
     *      If there is no computer it will return a {@link hudson.Launcher.DummyLauncher}, otherwise it
     *      will return a {@link hudson.Launcher.RemoteLauncher} instead.
     */
    @Override
    @NonNull
    public Launcher createLauncher(TaskListener listener) {
        SlaveComputer c = getComputer();
        if (c == null) {
            listener.error("Issue with creating launcher for agent " + name + ". Computer has been disconnected");
            return new Launcher.DummyLauncher(listener);
        } else {
            // TODO: ideally all the logic below should be inside the SlaveComputer class with proper locking to prevent race conditions,
            // but so far there is no locks for setNode() hence it requires serious refactoring

            // Ensure that the Computer instance still points to this node
            // Otherwise we may end up running the command on a wrong (reconnected) Node instance.
            Slave node = c.getNode();
            if (node != this) {
                String message = "Issue with creating launcher for agent " + name + ". Computer has been reconnected";
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, message, new IllegalStateException("Computer has been reconnected, this Node instance cannot be used anymore"));
                }
                return new Launcher.DummyLauncher(listener);
            }

            // RemoteLauncher requires an active Channel instance to operate correctly
            final Channel channel = c.getChannel();
            if (channel == null) {
                reportLauncherCreateError("The agent has not been fully initialized yet",
                                         "No remoting channel to the agent OR it has not been fully initialized yet", listener);
                return new Launcher.DummyLauncher(listener);
            }
            if (channel.isClosingOrClosed()) {
                reportLauncherCreateError("The agent is being disconnected",
                                         "Remoting channel is either in the process of closing down or has closed down", listener);
                return new Launcher.DummyLauncher(listener);
            }
            final Boolean isUnix = c.isUnix();
            if (isUnix == null) {
                // isUnix is always set when the channel is not null, so it should never happen
                reportLauncherCreateError("The agent has not been fully initialized yet",
                                         "Cannot determine if the agent is a Unix one, the System status request has not completed yet. " +
                                         "It is an invalid channel state, please report a bug to Jenkins if you see it.",
                                         listener);
                return new Launcher.DummyLauncher(listener);
            }

            return new RemoteLauncher(listener, channel, isUnix).decorateFor(this);
        }
    }

    private void reportLauncherCreateError(@NonNull String humanReadableMsg, @CheckForNull String exceptionDetails, @NonNull TaskListener listener) {
        String message = "Issue with creating launcher for agent " + name + ". " + humanReadableMsg;
        listener.error(message);
        if (LOGGER.isLoggable(Level.WARNING)) {
            // Send stacktrace to the log as well in order to diagnose the root cause of issues like JENKINS-38527
            LOGGER.log(Level.WARNING, message
                    + "Probably there is a race condition with Agent reconnection or disconnection, check other log entries",
                    new IllegalStateException(exceptionDetails != null ? exceptionDetails : humanReadableMsg));
        }
    }

    /**
     * Gets the corresponding computer object.
     *
     * @return
     *      this method can return null if there's no {@link Computer} object for this node,
     *      such as when this node has no executors at all.
     */
    @CheckForNull
    public SlaveComputer getComputer() {
        return (SlaveComputer) toComputer();
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

    private static final ThreadLocal<Boolean> insideReadResolve = ThreadLocal.withInitial(() -> false);

    /**
     * Invoked by XStream when this object is read into memory.
     */
    protected Object readResolve() {
        if (nodeProperties == null)
            nodeProperties = new DescribableList<>(this);
        previouslyAssignedLabels = new HashSet<>();
        insideReadResolve.set(true);
        try {
            _setLabelString(label);
        } finally {
            insideReadResolve.set(false);
        }
        return this;
    }

    @Override
    public SlaveDescriptor getDescriptor() {
        Descriptor d = Jenkins.get().getDescriptorOrDie(getClass());
        if (d instanceof SlaveDescriptor)
            return (SlaveDescriptor) d;
        throw new IllegalStateException(d.getClass() + " needs to extend from SlaveDescriptor");
    }

    public abstract static class SlaveDescriptor extends NodeDescriptor {
        public FormValidation doCheckNumExecutors(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        /**
         * Performs syntactical check on the remote FS for agents.
         */
        public FormValidation doCheckRemoteFS(@QueryParameter String value) throws IOException {
            if (Util.fixEmptyAndTrim(value) == null)
                return FormValidation.error(Messages.Slave_Remote_Director_Mandatory());

            if (value.startsWith("\\\\") || value.startsWith("/net/"))
                return FormValidation.warning(Messages.Slave_Network_Mounted_File_System_Warning());

            if (Util.isRelativePath(value)) {
                return FormValidation.warning(Messages.Slave_Remote_Relative_Path_Warning());
            }

            return FormValidation.ok();
        }

        /**
         * Returns the list of {@link ComputerLauncher} descriptors appropriate to the supplied {@link Slave}.
         *
         * @param it the {@link Slave} or {@code null} to assume the agent is of type {@link #clazz}.
         * @return the filtered list
         * @since 2.12
         */
        @NonNull
        @Restricted(NoExternalUse.class) // intended for use by Jelly EL only (plus hack in DelegatingComputerLauncher)
        public final List<Descriptor<ComputerLauncher>> computerLauncherDescriptors(@CheckForNull Slave it) {
            DescriptorExtensionList<ComputerLauncher, Descriptor<ComputerLauncher>> all =
                    Jenkins.get().getDescriptorList(
                            ComputerLauncher.class);
            return it == null ? DescriptorVisibilityFilter.applyType(clazz, all)
                    : DescriptorVisibilityFilter.apply(it, all);
        }

        /**
         * Returns the list of {@link RetentionStrategy} descriptors appropriate to the supplied {@link Slave}.
         *
         * @param it the {@link Slave} or {@code null} to assume the slave is of type {@link #clazz}.
         * @return the filtered list
         * @since 2.12
         */
        @NonNull
        @Restricted(NoExternalUse.class) // used by Jelly EL only
        public final List<Descriptor<RetentionStrategy<?>>> retentionStrategyDescriptors(@CheckForNull Slave it) {
            return it == null ? DescriptorVisibilityFilter.applyType(clazz, RetentionStrategy.all())
                    : DescriptorVisibilityFilter.apply(it, RetentionStrategy.all());
        }

        /**
         * Returns the list of {@link NodePropertyDescriptor} appropriate to the supplied {@link Slave}.
         *
         * @param it the {@link Slave} or {@code null} to assume the agent is of type {@link #clazz}.
         * @return the filtered list
         * @since 2.12
         */
        @NonNull
        @SuppressWarnings("unchecked") // used by Jelly EL only
        @Restricted(NoExternalUse.class) // used by Jelly EL only
        public final List<NodePropertyDescriptor> nodePropertyDescriptors(@CheckForNull Slave it) {
            List<NodePropertyDescriptor> result = new ArrayList<>();
            Collection<NodePropertyDescriptor> list =
                    (Collection) Jenkins.get().getDescriptorList(NodeProperty.class);
            for (NodePropertyDescriptor npd : it == null
                    ? DescriptorVisibilityFilter.applyType(clazz, list)
                    : DescriptorVisibilityFilter.apply(it, list)) {
                if (npd.isApplicable(clazz)) {
                    result.add(npd);
                }
            }
            return result;
        }

    }


//
// backward compatibility
//
    /**
     * Command line to launch the agent, like
     * "ssh myagent java -jar /path/to/hudson-remoting.jar"
     * @deprecated in 1.216
     */
    @Deprecated
    private transient String agentCommand; // this was called 'agentCommand' from the beginning; not an accidental 2016 rename

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
    private static final class GetClockDifference1 extends MasterToSlaveCallable<ClockDifference, IOException> {
        @Override
        public ClockDifference call() {
            // this method must be being invoked locally, which means the clock is in sync
            return new ClockDifference(0);
        }

        private Object writeReplace() {
            return new GetClockDifference2();
        }

        private static final long serialVersionUID = 1L;
    }

    private static final class GetClockDifference2 extends MasterToSlaveCallable<GetClockDifference3, IOException> {
        /**
         * Capture the time on the controller when this object is sent to remote, which is when
         * {@link GetClockDifference1#writeReplace()} is run.
         */
        private final long startTime = System.currentTimeMillis();

        @Override
        public GetClockDifference3 call() {
            return new GetClockDifference3(startTime);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final class GetClockDifference3 implements Serializable {
        private final long remoteTime = System.currentTimeMillis();
        private final long startTime;

        GetClockDifference3(long startTime) {
            this.startTime = startTime;
        }

        private Object readResolve() {
            long endTime = System.currentTimeMillis();
            return new ClockDifference((startTime + endTime) / 2 - remoteTime);
        }
    }

    /**
     * Determines the workspace root file name for those who really really need the shortest possible path name.
     */
    private static final String WORKSPACE_ROOT = SystemProperties.getString(Slave.class.getName() + ".workspaceRoot", "workspace");

    /**
     * Provides a collection of file names, which are accessible via /jnlpJars link.
     */
    private static final Set<String> ALLOWED_JNLPJARS_FILES = Set.of("agent.jar", "slave.jar", "remoting.jar", "jenkins-cli.jar", "hudson-cli.jar");
}
