/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.TaskListener;
import java.io.IOException;

/**
 * Factory of {@link ComputerLauncher}.
 *
 * When writing a {@link Cloud} implementation, one needs to dynamically create {@link ComputerLauncher}
 * by supplying a host name. This is the abstraction for that.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.383
 * @see ComputerLauncher
 */
public abstract class ComputerConnector implements Describable<ComputerConnector>, ExtensionPoint {
    /**
     * Creates a {@link ComputerLauncher} for connecting to the given host.
     *
     * @param host
     *      The host name / IP address of the machine to connect to.
     * @param listener
     *      If
     */
    public abstract ComputerLauncher launch(@NonNull String host, TaskListener listener) throws IOException, InterruptedException;

    @Override
    public ComputerConnectorDescriptor getDescriptor() {
        return (ComputerConnectorDescriptor) Describable.super.getDescriptor();
    }
}
