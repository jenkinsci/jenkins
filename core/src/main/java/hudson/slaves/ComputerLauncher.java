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

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tools.ant.util.DeweyDecimal;

/**
 * Extension point to allow control over how {@link Computer}s are "launched",
 * meaning how they get connected to their agent program.
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
 * @see ComputerConnector
 */
public abstract class ComputerLauncher extends AbstractDescribableImpl<ComputerLauncher> implements ExtensionPoint {
    /**
     * Returns true if this {@link ComputerLauncher} supports
     * programatic launch of the agent in the target {@link Computer}.
     */
    public boolean isLaunchSupported() {
        return true;
    }

    /**
     * Launches the agent for the given {@link Computer}.
     *
     * <p>
     * If the agent is launched successfully, {@link SlaveComputer#setChannel(InputStream, OutputStream, TaskListener, Channel.Listener)}
     * should be invoked in the end to notify Hudson of the established connection.
     * The operation could also fail, in which case there's no need to make any callback notification,
     * (except to notify the user of the failure through {@link StreamTaskListener}.)
     * Also note that the normal return of this method call does not necessarily signify a successful launch.
     * If someone programmatically calls this method and wants to find out if the launch was a success,
     * use {@link SlaveComputer#isOnline()} at the end.
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
    @Deprecated
    public void launch(SlaveComputer computer, StreamTaskListener listener) throws IOException , InterruptedException {
        throw new UnsupportedOperationException(getClass()+" must implement the launch method");
    }

    /**
     * Allows the {@link ComputerLauncher} to tidy-up after a disconnect.
     *
     * <p>
     * This method is invoked after the {@link Channel} to this computer is terminated.
     *
     * <p>
     * Disconnect operation is performed asynchronously, so there's no guarantee
     * that the corresponding {@link SlaveComputer} exists for the duration of the
     * operation.
     */
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        // to remain compatible with the legacy implementation that overrides the old signature
        afterDisconnect(computer,cast(listener));
    }

    /**
     * @deprecated as of 1.304
     *  Use {@link #afterDisconnect(SlaveComputer, TaskListener)}
     */
    @Deprecated
    public void afterDisconnect(SlaveComputer computer, StreamTaskListener listener) {
    }

    /**
     * Allows the {@link ComputerLauncher} to prepare for a disconnect.
     *
     * <p>
     * This method is invoked before the {@link Channel} to this computer is terminated,
     * thus the channel is still accessible from {@link SlaveComputer#getChannel()}.
     * If the channel is terminated unexpectedly, this method will not be invoked,
     * as the channel is already gone.
     *
     * <p>
     * Disconnect operation is performed asynchronously, so there's no guarantee
     * that the corresponding {@link SlaveComputer} exists for the duration of the
     * operation.
     */
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        // to remain compatible with the legacy implementation that overrides the old signature
        beforeDisconnect(computer,cast(listener));
    }

    /**
     * @deprecated as of 1.304
     *  Use {@link #beforeDisconnect(SlaveComputer, TaskListener)} 
     */
    @Deprecated
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
     *      {@link jenkins.model.Jenkins#getDescriptorList(Class)} for read access.
     */
    @Deprecated
    public static final DescriptorList<ComputerLauncher> LIST = new DescriptorList<ComputerLauncher>(ComputerLauncher.class);

    /**
     * Given the output of "java -version" in <code>r</code>, determine if this
     * version of Java is supported, or throw {@link IOException}.
     *
     * @param logger
     *            where to log the output
     * @param javaCommand
     *            the command executed, used for logging
     * @param r
     *            the output of "java -version"
     */
    protected static void checkJavaVersion(final PrintStream logger, String javaCommand,
                                    final BufferedReader r)
            throws IOException {
        String line;
        Pattern p = Pattern.compile("(?i)(?:java|openjdk) version \"([0-9.]+).*\"");
        while (null != (line = r.readLine())) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                final String versionStr = m.group(1);
                logger.println(Messages.ComputerLauncher_JavaVersionResult(javaCommand, versionStr));
                try {
                    if (new DeweyDecimal(versionStr).isLessThan(new DeweyDecimal("1.6"))) {
                        throw new IOException(Messages
                                .ComputerLauncher_NoJavaFound(line));
                    }
                } catch (NumberFormatException x) {
                    throw new IOException(Messages.ComputerLauncher_NoJavaFound(line));
                }
                return;
            }
        }
        logger.println(Messages.ComputerLauncher_UknownJavaVersion(javaCommand));
        throw new IOException(Messages.ComputerLauncher_UknownJavaVersion(javaCommand));
    }
}
