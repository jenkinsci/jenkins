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

package hudson.slaves;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import java.io.IOException;

/**
 * Receives notifications about status changes of {@link Computer}s.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.246
 */
public abstract class ComputerListener implements ExtensionPoint {

    /**
     * Called before a {@link ComputerLauncher} is asked to launch a connection with {@link Computer}.
     *
     * <p>
     * This enables you to do some configurable checks to see if we
     * want to bring this agent online or if there are considerations
     * that would keep us from doing so.
     *
     * <p>
     * Throwing {@link AbortException} would let you veto the launch operation. Other thrown exceptions
     * will also have the same effect, but their stack trace will be dumped, so they are meant for error situation.
     *
     * @param c
     *      Computer that's being launched. Never null.
     * @param taskListener
     *      Connected to the agent console log. Useful for reporting progress/errors on a lengthy operation.
     *      Never null.
     * @throws AbortException
     *      Exceptions will be recorded to the listener, and
     *      the computer will not become online.
     *
     * @since 1.402
     */
    public void preLaunch(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
    }

    /**
     * Called when an agent attempted to connect via {@link ComputerLauncher} but it failed.
     *
     * @param c
     *      Computer that was trying to launch. Never null.
     * @param taskListener
     *      Connected to the agent console log. Useful for reporting progress/errors on a lengthy operation.
     *      Never null.
     *
     * @since 1.402
     */
    public void onLaunchFailure(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
    }

    /**
     * Called before a {@link Computer} is marked online.
     *
     * <p>
     * This enables you to do some work on all the agents
     * as they get connected. Unlike {@link #onOnline(Computer, TaskListener)},
     * a failure to carry out this function normally will prevent
     * a computer from marked as online.
     *
     * @param channel
     *      This is the channel object to talk to the agent.
     *      (This is the same object returned by {@link Computer#getChannel()} once
     *      it's connected.
     * @param root
     *      The directory where this agent stores files.
     *      The same as {@link Node#getRootPath()}, except that method returns
     *      null until the agent is connected. So this parameter is passed explicitly instead.
     * @param listener
     *      This is connected to the launch log of the computer.
     *      Since this method is called synchronously from the thread
     *      that launches a computer, if this method performs a time-consuming
     *      operation, this listener should be notified of the progress.
     *      This is also a good listener for reporting problems.
     *
     * @throws IOException
     *      Exceptions will be recorded to the listener, and
     *      the computer will not become online.
     * @throws InterruptedException
     *      Exceptions will be recorded to the listener, and
     *      the computer will not become online.
     *
     * @since 1.295
     * @see #onOnline(Computer, TaskListener)
     */
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
    }

    /**
     * Called right after a {@link Computer} comes online.
     *
     * @deprecated as of 1.292
     *      Use {@link #onOnline(Computer, TaskListener)}
     */
    @Deprecated
    public void onOnline(Computer c) {}

    /**
     * Called right after a {@link Computer} comes online.
     *
     * <p>
     * This enables you to do some work on all the agents
     * as they get connected.
     *
     * Any thrown {@link Exception}s will be recorded to the listener.
     * No {@link Exception} will put the computer offline, however
     * any {@link Error} will put the computer offline
     * since they indicate unrecoverable conditions.
     *
     * <p>
     * Starting Hudson 1.312, this method is also invoked for the master, not just for agents.
     *
     * @param listener
     *      This is connected to the launch log of the computer or Jenkins master.
     *      Since this method is called synchronously from the thread
     *      that launches a computer, if this method performs a time-consuming
     *      operation, this listener should be notified of the progress.
     *      This is also a good listener for reporting problems.
     *
     * @throws IOException
     *      Exceptions will be recorded to the listener. Note that
     *      throwing an exception doesn't put the computer offline.
     * @throws InterruptedException
     *      Exceptions will be recorded to the listener. Note that
     *      throwing an exception doesn't put the computer offline.
     *
     * @see #preOnline(Computer, Channel, FilePath, TaskListener)
     */
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        // compatibility
        onOnline(c);
    }

    /**
     * Called right after a {@link Computer} went offline.
     *
     * @deprecated since 1.571. Use {@link #onOffline(Computer, OfflineCause)} instead.
     */
    @Deprecated
    public void onOffline(Computer c) {}

    /**
     * Called right after a {@link Computer} went offline.
     *
     * @since 1.571
     */
    public void onOffline(@NonNull Computer c, @CheckForNull OfflineCause cause) {
        onOffline(c);
    }

    /**
     * Indicates that the computer was marked as temporarily online by the administrator.
     * This is the reverse operation of {@link #onTemporarilyOffline(Computer, OfflineCause)}
     *
     * @since 1.452
     */
    public void onTemporarilyOnline(Computer c) {}
    /**
     * Indicates that the computer was marked as temporarily offline by the administrator.
     * This is the reverse operation of {@link #onTemporarilyOnline(Computer)}
     *
     * @since 1.452
     */

    public void onTemporarilyOffline(Computer c, OfflineCause cause) {}

    /**
     * Called when configuration of the node was changed, a node is added/removed, etc.
     *
     * <p>
     * This callback is to signal when there's any change to the list of agents registered to the system,
     * including addition, removal, changing of the setting, and so on.
     *
     * @since 1.377
     */
    public void onConfigurationChange() {}

    /**
     * Indicates that the computer has become idle.
     *
     * @since 2.476
     */
    public void onIdle(Computer c) {}

    /**
     * Registers this {@link ComputerListener} so that it will start receiving events.
     *
     * @deprecated as of 1.286
     *      put {@link Extension} on your class to have it auto-registered.
     */
    @Deprecated
    public final void register() {
        all().add(this);
    }

    /**
     * Unregisters this {@link ComputerListener} so that it will never receive further events.
     *
     * <p>
     * Unless {@link ComputerListener} is unregistered, it will never be a subject of GC.
     */
    public final boolean unregister() {
        return all().remove(this);
    }

    /**
     * All the registered {@link ComputerListener}s.
     */
    public static ExtensionList<ComputerListener> all() {
        return ExtensionList.lookup(ComputerListener.class);
    }
}
