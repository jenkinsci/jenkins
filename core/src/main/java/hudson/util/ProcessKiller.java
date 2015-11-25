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

package hudson.util;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.io.IOException;
import java.io.Serializable;

/**
 * Extension point that defines more elaborate way of killing processes, such as
 * sudo or pfexec, for {@link ProcessTree}.
 *
 * <h2>Lifecycle</h2>
 * <p>
 * Each implementation of {@link ProcessKiller} is instantiated once on the master.
 * Whenever a process needs to be killed, those implementations are serialized and sent over
 * to the appropriate slave, then the {@link #kill(ProcessTree.OSProcess)} method is invoked
 * to attempt to kill the process.
 *
 * <p>
 * One of the consequences of this design is that the implementation should be stateless
 * and concurrent-safe. That is, the {@link #kill(ProcessTree.OSProcess)} method can be invoked by multiple threads
 * concurrently on the single instance.
 *
 * <p>
 * Another consequence of this design is that if your {@link ProcessKiller} requires configuration,
 * it needs to be serializable, and configuration needs to be updated atomically, as another
 * thread may be calling into {@link #kill(ProcessTree.OSProcess)} just when you are updating your configuration.
 *
 * @author jpederzolli
 * @author Kohsuke Kawaguchi
 * @since 1.362
 */
public abstract class ProcessKiller implements ExtensionPoint, Serializable {
    /**
     * Returns all the registered {@link ProcessKiller} descriptors.
     */
    public static ExtensionList<ProcessKiller> all() {
        return ExtensionList.lookup(ProcessKiller.class);
    }

    /**
     * Attempts to kill the given process.
     *
     * @param process process to be killed. Always a {@linkplain ProcessTree.Local local process}.
     * @return
     *      true if the killing was successful, and Hudson won't try to use other {@link ProcessKiller}
     *      implementations to kill the process. false if the killing failed or is unattempted, and Hudson will continue
     *      to use the rest of the {@link ProcessKiller} implementations to try to kill the process.
     * @throws IOException
     *      The caller will log this exception and otherwise treat as if the method returned false, and moves on
     *      to the next killer.
     * @throws InterruptedException
     *      if the callee performs a time consuming operation and if the thread is canceled, do not catch
     *      {@link InterruptedException} and just let it thrown from the method.
     */
    public abstract boolean kill(ProcessTree.OSProcess process) throws IOException, InterruptedException;

    private static final long serialVersionUID = 1L;
}
