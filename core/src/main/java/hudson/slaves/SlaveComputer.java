/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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
package hudson.slaves;

import edu.umd.cs.findbugs.annotations.OverrideMustInvoke;
import edu.umd.cs.findbugs.annotations.When;
import hudson.AbortException;
import hudson.FilePath;
import hudson.Util;
import hudson.console.ConsoleLogFilter;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.slaves.OfflineCause.ChannelTermination;
import hudson.util.Futures;
import hudson.util.IOUtils;
import hudson.util.NullStream;
import hudson.util.RingBufferLogHandler;
import hudson.util.StreamTaskListener;
import hudson.util.io.RewindableFileOutputStream;
import hudson.util.io.RewindableRotatingFileOutputStream;
import jenkins.model.Jenkins;
import jenkins.security.ChannelConfigurator;
import jenkins.security.MasterToSlaveCallable;
import jenkins.slaves.EncryptedSlaveAgentJnlpFile;
import jenkins.slaves.JnlpSlaveAgentProtocol;
import jenkins.slaves.systemInfo.SlaveSystemInfo;
import jenkins.util.SystemProperties;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static hudson.slaves.SlaveComputer.LogHolder.SLAVE_LOG_HANDLER;


/**
 * {@link Computer} for {@link Slave}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class SlaveComputer extends Computer {
    private volatile Channel channel;
    private volatile transient boolean acceptingTasks = true;
    private Charset defaultCharset;
    private Boolean isUnix;
    /**
     * Effective {@link ComputerLauncher} that hides the details of
     * how we launch a agent agent on this computer.
     *
     * <p>
     * This is normally the same as {@link Slave#getLauncher()} but
     * can be different. See {@link #grabLauncher(Node)}.
     */
    private ComputerLauncher launcher;

    /**
     * Perpetually writable log file.
     */
    private final RewindableFileOutputStream log;

    /**
     * {@link StreamTaskListener} that wraps {@link #log}, hence perpetually writable.
     */
    private final TaskListener taskListener;


    /**
     * Number of failed attempts to reconnect to this node
     * (so that if we keep failing to reconnect, we can stop
     * trying.)
     */
    private transient int numRetryAttempt;

    /**
     * Tracks the status of the last launch operation, which is always asynchronous.
     * This can be used to wait for the completion, or cancel the launch activity.
     */
    private volatile Future<?> lastConnectActivity = null;

    private Object constructed = new Object();

    private transient volatile String absoluteRemoteFs;

    public SlaveComputer(Slave slave) {
        super(slave);
        this.log = new RewindableRotatingFileOutputStream(getLogFile(), 10);
        this.taskListener = new StreamTaskListener(decorate(this.log));
        assert slave.getNumExecutors()!=0 : "Computer created with 0 executors";
    }

    /**
     * Uses {@link ConsoleLogFilter} to decorate logger.
     */
    private OutputStream decorate(OutputStream os) {
        for (ConsoleLogFilter f : ConsoleLogFilter.all()) {
            try {
                os = f.decorateLogger(this,os);
            } catch (IOException|InterruptedException e) {
                LOGGER.log(Level.WARNING, "Failed to filter log with "+f, e);
            }
        }
        return os;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @OverrideMustInvoke(When.ANYTIME)
    public boolean isAcceptingTasks() {
        // our boolean flag is an override on any additional programmatic reasons why this agent might not be
        // accepting tasks.
        return acceptingTasks && super.isAcceptingTasks();
    }

    /**
     * @since 1.498
     */
    public String getJnlpMac() {
        return JnlpSlaveAgentProtocol.SLAVE_SECRET.mac(getName());
    }

    /**
     * Allows suspension of tasks being accepted by the agent computer. While this could be called by a
     * {@linkplain hudson.slaves.ComputerLauncher} or a {@linkplain hudson.slaves.RetentionStrategy}, such usage
     * can result in fights between multiple actors calling setting differential values. A better approach
     * is to override {@link hudson.slaves.RetentionStrategy#isAcceptingTasks(hudson.model.Computer)} if the
     * {@link hudson.slaves.RetentionStrategy} needs to control availability.
     *
     * @param acceptingTasks {@code true} if the agent can accept tasks.
     */
    public void setAcceptingTasks(boolean acceptingTasks) {
        this.acceptingTasks = acceptingTasks;
    }

    @Override
    public Boolean isUnix() {
        return isUnix;
    }

    @CheckForNull
    @Override
    public Slave getNode() {
        Node node = super.getNode();
        if (node == null || node instanceof Slave) {
            return (Slave)node;
        } else {
            logger.log(Level.WARNING, "found an unexpected kind of node {0} from {1} with nodeName={2}", new Object[] {node, this, nodeName});
            return null;
        }
    }

    /**
     * Return the {@code TaskListener} for this SlaveComputer. Never null
     * @since 2.9
     */
    public TaskListener getListener() {
        return taskListener;
    }

    @Override
    public String getIcon() {
        Future<?> l = lastConnectActivity;
        if(l!=null && !l.isDone())
            return "computer-flash.gif";
        return super.getIcon();
    }

    /**
     * @deprecated since 2008-05-20.
     */
    @Deprecated @Override
    public boolean isJnlpAgent() {
        return launcher instanceof JNLPLauncher;
    }

    @Override
    public boolean isLaunchSupported() {
        return launcher.isLaunchSupported();
    }

    public ComputerLauncher getLauncher() {
        return launcher;
    }

    protected Future<?> _connect(boolean forceReconnect) {
        if(channel!=null)   return Futures.precomputed(null);
        if(!forceReconnect && isConnecting())
            return lastConnectActivity;
        if(forceReconnect && isConnecting())
            logger.fine("Forcing a reconnect on "+getName());

        closeChannel();
        return lastConnectActivity = Computer.threadPoolForRemoting.submit(new java.util.concurrent.Callable<Object>() {
            public Object call() throws Exception {
                // do this on another thread so that the lengthy launch operation
                // (which is typical) won't block UI thread.

                ACL.impersonate(ACL.SYSTEM);    // background activity should run like a super user

                try {
                    log.rewind();
                    try {
                        for (ComputerListener cl : ComputerListener.all())
                            cl.preLaunch(SlaveComputer.this, taskListener);
                        offlineCause = null;
                        launcher.launch(SlaveComputer.this, taskListener);
                    } catch (AbortException e) {
                        taskListener.error(e.getMessage());
                        throw e;
                    } catch (IOException e) {
                        Util.displayIOException(e,taskListener);
                        e.printStackTrace(taskListener.error(Messages.ComputerLauncher_unexpectedError()));
                        throw e;
                    } catch (InterruptedException e) {
                        e.printStackTrace(taskListener.error(Messages.ComputerLauncher_abortedLaunch()));
                        throw e;
                    } catch (Exception e) {
                        e.printStackTrace(taskListener.error(Messages.ComputerLauncher_unexpectedError()));
                        throw e;
                    }
                } finally {
                    if (channel==null && offlineCause == null) {
                        offlineCause = new OfflineCause.LaunchFailed();
                        for (ComputerListener cl : ComputerListener.all())
                            cl.onLaunchFailure(SlaveComputer.this, taskListener);
                    }
                }

                if (channel==null)
                    throw new IOException("Agent failed to connect, even though the launcher didn't report it. See the log output for details.");
                return null;
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        if (launcher instanceof ExecutorListener) {
            ((ExecutorListener)launcher).taskAccepted(executor, task);
        }

        //getNode() can return null at indeterminate times when nodes go offline
        Slave node = getNode();
        if (node != null && node.getRetentionStrategy() instanceof ExecutorListener) {
            ((ExecutorListener)node.getRetentionStrategy()).taskAccepted(executor, task);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        super.taskCompleted(executor, task, durationMS);
        if (launcher instanceof ExecutorListener) {
            ((ExecutorListener)launcher).taskCompleted(executor, task, durationMS);
        }
        RetentionStrategy r = getRetentionStrategy();
        if (r instanceof ExecutorListener) {
            ((ExecutorListener) r).taskCompleted(executor, task, durationMS);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        if (launcher instanceof ExecutorListener) {
            ((ExecutorListener)launcher).taskCompletedWithProblems(executor, task, durationMS, problems);
        }
        RetentionStrategy r = getRetentionStrategy();
        if (r instanceof ExecutorListener) {
            ((ExecutorListener) r).taskCompletedWithProblems(executor, task, durationMS, problems);
        }
    }

    @Override
    public boolean isConnecting() {
        Future<?> l = lastConnectActivity;
        return isOffline() && l!=null && !l.isDone();
    }

    public OutputStream openLogFile() {
        try {
            log.rewind();
            return log;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create log file "+getLogFile(),e);
            return new NullStream();
        }
    }

    private final Object channelLock = new Object();

    public void setChannel(InputStream in, OutputStream out, TaskListener taskListener, Channel.Listener listener) throws IOException, InterruptedException {
        setChannel(in,out,taskListener.getLogger(),listener);
    }

    /**
     * Creates a {@link Channel} from the given stream and sets that to this agent.
     *
     * @param in
     *      Stream connected to the remote "slave.jar". It's the caller's responsibility to do
     *      buffering on this stream, if that's necessary.
     * @param out
     *      Stream connected to the remote peer. It's the caller's responsibility to do
     *      buffering on this stream, if that's necessary.
     * @param launchLog
     *      If non-null, receive the portion of data in <tt>is</tt> before
     *      the data goes into the "binary mode". This is useful
     *      when the established communication channel might include some data that might
     *      be useful for debugging/trouble-shooting.
     * @param listener
     *      Gets a notification when the channel closes, to perform clean up. Can be null.
     *      By the time this method is called, the cause of the termination is reported to the user,
     *      so the implementation of the listener doesn't need to do that again.
     */
    public void setChannel(InputStream in, OutputStream out, OutputStream launchLog, Channel.Listener listener) throws IOException, InterruptedException {
        ChannelBuilder cb = new ChannelBuilder(nodeName,threadPoolForRemoting)
            .withMode(Channel.Mode.NEGOTIATE)
            .withHeaderStream(launchLog);

        for (ChannelConfigurator cc : ChannelConfigurator.all()) {
            cc.onChannelBuilding(cb,this);
        }

        Channel channel = cb.build(in,out);
        setChannel(channel,launchLog,listener);
    }

    /**
     * Shows {@link Channel#classLoadingCount}.
     * @since 1.495
     */
    public int getClassLoadingCount() throws IOException, InterruptedException {
        return channel.call(new LoadingCount(false));
    }

    /**
     * Shows {@link Channel#classLoadingPrefetchCacheCount}.
     * @return -1 in case that capability is not supported
     * @since 1.519
     */
    public int getClassLoadingPrefetchCacheCount() throws IOException, InterruptedException {
        if (!channel.remoteCapability.supportsPrefetch()) {
            return -1;
        }
        return channel.call(new LoadingPrefetchCacheCount());
    }

    /**
     * Shows {@link Channel#resourceLoadingCount}.
     * @since 1.495
     */
    public int getResourceLoadingCount() throws IOException, InterruptedException {
        return channel.call(new LoadingCount(true));
    }

    /**
     * Shows {@link Channel#classLoadingTime}.
     * @since 1.495
     */
    public long getClassLoadingTime() throws IOException, InterruptedException {
        return channel.call(new LoadingTime(false));
    }

    /**
     * Shows {@link Channel#resourceLoadingTime}.
     * @since 1.495
     */
    public long getResourceLoadingTime() throws IOException, InterruptedException {
        return channel.call(new LoadingTime(true));
    }

    /**
     * Returns the remote FS root absolute path or {@code null} if the agent is off-line. The absolute path may change
     * between connections if the connection method does not provide a consistent working directory and the node's
     * remote FS is specified as a relative path.
     *
     * @return the remote FS root absolute path or {@code null} if the agent is off-line.
     * @since 1.606
     */
    @CheckForNull
    public String getAbsoluteRemoteFs() {
        return channel == null ? null : absoluteRemoteFs;
    }

    static class LoadingCount extends MasterToSlaveCallable<Integer,RuntimeException> {
        private final boolean resource;
        LoadingCount(boolean resource) {
            this.resource = resource;
        }
        @Override public Integer call() {
            Channel c = Channel.current();
            return resource ? c.resourceLoadingCount.get() : c.classLoadingCount.get();
        }
    }

    static class LoadingPrefetchCacheCount extends MasterToSlaveCallable<Integer,RuntimeException> {
        @Override public Integer call() {
            return Channel.current().classLoadingPrefetchCacheCount.get();
        }
    }

    static class LoadingTime extends MasterToSlaveCallable<Long,RuntimeException> {
        private final boolean resource;
        LoadingTime(boolean resource) {
            this.resource = resource;
        }
        @Override public Long call() {
            Channel c = Channel.current();
            return resource ? c.resourceLoadingTime.get() : c.classLoadingTime.get();
        }
    }

    /**
     * Sets up the connection through an existing channel.
     * @param channel the channel to use; <strong>warning:</strong> callers are expected to have called {@link ChannelConfigurator} already
     * @since 1.444
     */
    public void setChannel(Channel channel, OutputStream launchLog, Channel.Listener listener) throws IOException, InterruptedException {
        if(this.channel!=null)
            throw new IllegalStateException("Already connected");

        final TaskListener taskListener = new StreamTaskListener(launchLog);
        PrintStream log = taskListener.getLogger();

        channel.setProperty(SlaveComputer.class, this);

        channel.addListener(new Channel.Listener() {
            @Override
            public void onClosed(Channel c, IOException cause) {
                // Orderly shutdown will have null exception
                if (cause!=null) {
                    offlineCause = new ChannelTermination(cause);
                    cause.printStackTrace(taskListener.error("Connection terminated"));
                } else {
                    taskListener.getLogger().println("Connection terminated");
                }
                closeChannel();
                try {
                    launcher.afterDisconnect(SlaveComputer.this, taskListener);
                } catch (Throwable t) {
                    LogRecord lr = new LogRecord(Level.SEVERE,
                            "Launcher {0}'s afterDisconnect method propagated an exception when {1}'s connection was closed: {2}");
                    lr.setThrown(t);
                    lr.setParameters(new Object[]{launcher, SlaveComputer.this.getName(), t.getMessage()});
                    logger.log(lr);
                }
            }
        });
        if(listener!=null)
            channel.addListener(listener);

        String slaveVersion = channel.call(new SlaveVersion());
        log.println("Slave.jar version: " + slaveVersion);

        boolean _isUnix = channel.call(new DetectOS());
        log.println(_isUnix? hudson.model.Messages.Slave_UnixSlave():hudson.model.Messages.Slave_WindowsSlave());

        String defaultCharsetName = channel.call(new DetectDefaultCharset());

        Slave node = getNode();
        if (node == null) { // Node has been disabled/removed during the connection
            throw new IOException("Node "+nodeName+" has been deleted during the channel setup");
        }

        String remoteFS = node.getRemoteFS();
        if (Util.isRelativePath(remoteFS)) {
            remoteFS = channel.call(new AbsolutePath(remoteFS));
            log.println("NOTE: Relative remote path resolved to: "+remoteFS);
        }
        if(_isUnix && !remoteFS.contains("/") && remoteFS.contains("\\"))
            log.println("WARNING: "+remoteFS
                    +" looks suspiciously like Windows path. Maybe you meant "+remoteFS.replace('\\','/')+"?");
        FilePath root = new FilePath(channel,remoteFS);

        // reference counting problem is known to happen, such as JENKINS-9017, and so as a preventive measure
        // we pin the base classloader so that it'll never get GCed. When this classloader gets released,
        // it'll have a catastrophic impact on the communication.
        channel.pinClassLoader(getClass().getClassLoader());

        channel.call(new SlaveInitializer(DEFAULT_RING_BUFFER_SIZE));
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            for (ComputerListener cl : ComputerListener.all()) {
                cl.preOnline(this,channel,root,taskListener);
            }
        } finally {
            SecurityContextHolder.setContext(old);
        }

        offlineCause = null;

        // update the data structure atomically to prevent others from seeing a channel that's not properly initialized yet
        synchronized(channelLock) {
            if(this.channel!=null) {
                // check again. we used to have this entire method in a big sycnhronization block,
                // but Channel constructor blocks for an external process to do the connection
                // if CommandLauncher is used, and that cannot be interrupted because it blocks at InputStream.
                // so if the process hangs, it hangs the thread in a lock, and since Hudson will try to relaunch,
                // we'll end up queuing the lot of threads in a pseudo deadlock.
                // This implementation prevents that by avoiding a lock. HUDSON-1705 is likely a manifestation of this.
                channel.close();
                throw new IllegalStateException("Already connected");
            }
            isUnix = _isUnix;
            numRetryAttempt = 0;
            this.channel = channel;
            this.absoluteRemoteFs = remoteFS;
            defaultCharset = Charset.forName(defaultCharsetName);

            synchronized (statusChangeLock) {
                statusChangeLock.notifyAll();
            }
        }
        old = ACL.impersonate(ACL.SYSTEM);
        try {
            for (ComputerListener cl : ComputerListener.all()) {
                cl.onOnline(this,taskListener);
            }
        } finally {
            SecurityContextHolder.setContext(old);
        }
        log.println("Agent successfully connected and online");
        Jenkins.getInstance().getQueue().scheduleMaintenance();
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    public Charset getDefaultCharset() {
        return defaultCharset;
    }

    public List<LogRecord> getLogRecords() throws IOException, InterruptedException {
        if(channel==null)
            return Collections.emptyList();
        else
            return channel.call(new SlaveLogFetcher());
    }

    @RequirePOST
    public HttpResponse doDoDisconnect(@QueryParameter String offlineMessage) throws IOException, ServletException {
        if (channel!=null) {
            //does nothing in case computer is already disconnected
            checkPermission(DISCONNECT);
            offlineMessage = Util.fixEmptyAndTrim(offlineMessage);
            disconnect(new OfflineCause.UserCause(User.current(), offlineMessage));
        }
        return new HttpRedirect(".");
    }

    @Override
    public Future<?> disconnect(OfflineCause cause) {
        super.disconnect(cause);
        return Computer.threadPoolForRemoting.submit(new Runnable() {
            public void run() {
                // do this on another thread so that any lengthy disconnect operation
                // (which could be typical) won't block UI thread.
                launcher.beforeDisconnect(SlaveComputer.this, taskListener);
                closeChannel();
                launcher.afterDisconnect(SlaveComputer.this, taskListener);
            }
        });
    }

    @RequirePOST
    public void doLaunchSlaveAgent(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(channel!=null) {
            req.getView(this,"already-launched.jelly").forward(req, rsp);
            return;
        }

        connect(true);

        // TODO: would be nice to redirect the user to "launching..." wait page,
        // then spend a few seconds there and poll for the completion periodically.
        rsp.sendRedirect("log");
    }

    public void tryReconnect() {
        numRetryAttempt++;
        if(numRetryAttempt<6 || (numRetryAttempt%12)==0) {
            // initially retry several times quickly, and after that, do it infrequently.
            logger.info("Attempting to reconnect "+nodeName);
            connect(true);
        }
    }

    /**
     * Serves jar files for JNLP agents.
     *
     * @deprecated since 2008-08-18.
     *      This URL binding is no longer used and moved up directly under to {@link jenkins.model.Jenkins},
     *      but it's left here for now just in case some old JNLP agents request it.
     */
    @Deprecated
    public Slave.JnlpJar getJnlpJars(String fileName) {
        return new Slave.JnlpJar(fileName);
    }

    @WebMethod(name="slave-agent.jnlp")
    public HttpResponse doSlaveAgentJnlp(StaplerRequest req, StaplerResponse res) throws IOException, ServletException {
        return new EncryptedSlaveAgentJnlpFile(this, "slave-agent.jnlp.jelly", getName(), CONNECT);
    }

    @Override
    protected void kill() {
        super.kill();
        closeChannel();
        IOUtils.closeQuietly(log);

        try {
            Util.deleteRecursive(getLogDir());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to delete agent logs", ex);
        }
    }

    public RetentionStrategy getRetentionStrategy() {
        Slave n = getNode();
        return n==null ? RetentionStrategy.INSTANCE : n.getRetentionStrategy();
    }

    /**
     * If still connected, disconnect.
     */
    private void closeChannel() {
        // TODO: race condition between this and the setChannel method.
        Channel c;
        synchronized (channelLock) {
            c = channel;
            channel = null;
            absoluteRemoteFs = null;
            isUnix = null;
        }
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to terminate channel to " + getDisplayName(), e);
            }
            for (ComputerListener cl : ComputerListener.all())
                cl.onOffline(this, offlineCause);
        }
    }

    @Override
    protected void setNode(final Node node) {
        super.setNode(node);
        launcher = grabLauncher(node);

        // maybe the configuration was changed to relaunch the agent, so try to re-launch now.
        // "constructed==null" test is an ugly hack to avoid launching before the object is fully
        // constructed.
        if(constructed!=null) {
            if (node instanceof Slave) {
                Queue.withLock(new Runnable() {
                    @Override
                    public void run() {
                        ((Slave)node).getRetentionStrategy().check(SlaveComputer.this);
                    }
                });
            } else {
                connect(false);
            }
        }
    }

    /**
     * Grabs a {@link ComputerLauncher} out of {@link Node} to keep it in this {@link Computer}.
     * The returned launcher will be set to {@link #launcher} and used to carry out the actual launch operation.
     *
     * <p>
     * Subtypes that needs to decorate {@link ComputerLauncher} can do so by overriding this method.
     * This is useful for {@link SlaveComputer}s for clouds for example, where one normally needs
     * additional pre-launch step (such as waiting for the provisioned node to become available)
     * before the user specified launch step (like SSH connection) kicks in.
     *
     * @see ComputerLauncherFilter
     */
    protected ComputerLauncher grabLauncher(Node node) {
        return ((Slave)node).getLauncher();
    }

    /**
     * Get the agent version
     */
    public String getSlaveVersion() throws IOException, InterruptedException {
        return channel.call(new SlaveVersion());
    }

    /**
     * Get the OS description.
     */
    public String getOSDescription() throws IOException, InterruptedException {
        return channel.call(new DetectOS()) ? "Unix" : "Windows";
    }

    private static final Logger logger = Logger.getLogger(SlaveComputer.class.getName());

    private static final class SlaveVersion extends MasterToSlaveCallable<String,IOException> {
        public String call() throws IOException {
            try { return Launcher.VERSION; }
            catch (Throwable ex) { return "< 1.335"; } // Older slave.jar won't have VERSION
        }
    }
    private static final class DetectOS extends MasterToSlaveCallable<Boolean,IOException> {
        public Boolean call() throws IOException {
            return File.pathSeparatorChar==':';
        }
    }

    private static final class AbsolutePath extends MasterToSlaveCallable<String,IOException> {

        private static final long serialVersionUID = 1L;

        private final String relativePath;

        private AbsolutePath(String relativePath) {
            this.relativePath = relativePath;
        }

        public String call() throws IOException {
            return new File(relativePath).getAbsolutePath();
        }
    }

    private static final class DetectDefaultCharset extends MasterToSlaveCallable<String,IOException> {
        public String call() throws IOException {
            return Charset.defaultCharset().name();
        }
    }

    /**
     * Puts the {@link #SLAVE_LOG_HANDLER} into a separate class so that loading this class
     * in JVM doesn't end up loading tons of additional classes.
     */
    static final class LogHolder {
        /**
         * This field is used on each agent to record logs on the agent.
         */
        static RingBufferLogHandler SLAVE_LOG_HANDLER;
    }

    private static class SlaveInitializer extends MasterToSlaveCallable<Void,RuntimeException> {
        final int ringBufferSize;

        public SlaveInitializer(int ringBufferSize) {
            this.ringBufferSize = ringBufferSize;
        }

        public Void call() {
            SLAVE_LOG_HANDLER = new RingBufferLogHandler(ringBufferSize);

            // avoid double installation of the handler. JNLP slaves can reconnect to the master multiple times
            // and each connection gets a different RemoteClassLoader, so we need to evict them by class name,
            // not by their identity.
            for (Handler h : LOGGER.getHandlers()) {
                if (h.getClass().getName().equals(SLAVE_LOG_HANDLER.getClass().getName()))
                    LOGGER.removeHandler(h);
            }
            LOGGER.addHandler(SLAVE_LOG_HANDLER);

            // remove Sun PKCS11 provider if present. See http://wiki.jenkins-ci.org/display/JENKINS/Solaris+Issue+6276483
            try {
                Security.removeProvider("SunPKCS11-Solaris");
            } catch (SecurityException e) {
                // ignore this error.
            }

            Channel.current().setProperty("slave",Boolean.TRUE); // indicate that this side of the channel is the slave side.

            return null;
        }
        private static final long serialVersionUID = 1L;
        private static final Logger LOGGER = Logger.getLogger("");
    }

    /**
     * Obtains a {@link VirtualChannel} that allows some computation to be performed on the master.
     * This method can be called from any thread on the master, or from agent (more precisely,
     * it only works from the remoting request-handling thread in agents, which means if you've started
     * separate thread on agents, that'll fail.)
     *
     * @return null if the calling thread doesn't have any trace of where its master is.
     * @since 1.362
     */
    public static VirtualChannel getChannelToMaster() {
        if (Jenkins.getInstanceOrNull()!=null) // check if calling thread is on master or on slave
            return FilePath.localChannel;

        // if this method is called from within the agent computation thread, this should work
        Channel c = Channel.current();
        if (c!=null && Boolean.TRUE.equals(c.getProperty("slave")))
            return c;

        return null;
    }

    /**
     * Helper method for Jelly.
     */
    public static List<SlaveSystemInfo> getSystemInfoExtensions() {
        return SlaveSystemInfo.all();
    }

    private static class SlaveLogFetcher extends MasterToSlaveCallable<List<LogRecord>,RuntimeException> {
        public List<LogRecord> call() {
            return new ArrayList<LogRecord>(SLAVE_LOG_HANDLER.getView());
        }
    }

    // use RingBufferLogHandler class name to configure for backward compatibility
    private static final int DEFAULT_RING_BUFFER_SIZE = SystemProperties.getInteger(RingBufferLogHandler.class.getName() + ".defaultSize", 256);

    private static final Logger LOGGER = Logger.getLogger(SlaveComputer.class.getName());
}
