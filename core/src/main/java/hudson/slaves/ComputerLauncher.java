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

import hudson.ExtensionPoint;
import hudson.Extension;
import hudson.model.*;
import hudson.remoting.Channel;
import hudson.util.DescriptorList;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Extension point to allow control over how {@link Computer}s are "launched",
 * meaning how they get connected to their slave agent program.
 *
 * <h2>Associated View</h2>
 * <dl>
 * <dt>main.jelly</dt>
 * <dd>
 * This page will be rendered into the top page of the computer (/computer/NAME/)
 * Useful for showing launch related commands and status reports.
 * </dl>
 *
 * @author Stephen Connolly
 * @since 24-Apr-2008 22:12:35
 */
public abstract class ComputerLauncher extends AbstractDescribableImpl<ComputerLauncher> implements ExtensionPoint {
    /**
     * Returns true if this {@link ComputerLauncher} supports
     * programatic launch of the slave agent in the target {@link Computer}.
     */
    public boolean isLaunchSupported() {
        return true;
    }

    /**
     * Launches the slave agent for the given {@link Computer}.
     *
     * <p>
     * If the slave agent is launched successfully, {@link SlaveComputer#setChannel(InputStream, OutputStream, OutputStream, Channel.Listener)}
     * should be invoked in the end to notify Hudson of the established connection.
     * The operation could also fail, in which case there's no need to make any callback notification,
     * (except to notify the user of the failure through {@link StreamTaskListener}.)
     *
     * <p>
     * This method must operate synchronously. Asynchrony is provided by {@link Computer#connect(boolean)} and
     * its correct operation depends on this. 
     *
     * @param listener
     *      The progress of the launch, as well as any error, should be sent to this listener.
     *
     * @throws IOException
     *      if the method throws an {@link IOException} or {@link InterruptedException}, the launch was considered
     *      a failure and the stack trace is reported into the listener. This handling is just so that the implementation
     *      of this method doesn't have to dilligently catch those exceptions.
     */
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException , InterruptedException {
        // to remain compatible with the legacy implementation that overrides the old signature
        launch(computer,cast(listener));
    }

    /**
     * @deprecated as of 1.304
     *  Use {@link #launch(SlaveComputer, TaskListener)}
     */
    public void launch(SlaveComputer computer, StreamTaskListener listener) throws IOException , InterruptedException {
        throw new UnsupportedOperationException(getClass()+" must implement the launch method");
    }

    /**
     * Allows the {@link ComputerLauncher} to tidy-up after a disconnect.
     */
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        // to remain compatible with the legacy implementation that overrides the old signature
        afterDisconnect(computer,cast(listener));
    }

    /**
     * @deprecated as of 1.304
     *  Use {@link #afterDisconnect(SlaveComputer, TaskListener)}
     */
    public void afterDisconnect(SlaveComputer computer, StreamTaskListener listener) {
    }

    /**
     * Allows the {@link ComputerLauncher} to prepare for a disconnect.
     */
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        // to remain compatible with the legacy implementation that overrides the old signature
        beforeDisconnect(computer,cast(listener));
    }

    /**
     * @deprecated as of 1.304
     *  Use {@link #beforeDisconnect(SlaveComputer, TaskListener)} 
     */
    public void beforeDisconnect(SlaveComputer computer, StreamTaskListener listener) {
    }

    private StreamTaskListener cast(TaskListener listener) {
        if (listener instanceof StreamTaskListener)
            return (StreamTaskListener) listener;
        return new StreamTaskListener(listener.getLogger());
    }

    /**
     * All registered {@link ComputerLauncher} implementations.
     *
     * @deprecated as of 1.281
     *      Use {@link Extension} for registration, and use
     *      {@link Hudson#getDescriptorList(Class)} for read access.
     */
    public static final DescriptorList<ComputerLauncher> LIST = new DescriptorList<ComputerLauncher>(ComputerLauncher.class);
}
