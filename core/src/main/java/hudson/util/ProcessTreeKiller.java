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
package hudson.util;

import hudson.EnvVars;
import hudson.util.ProcessTree.OSProcess;

import java.util.Map;

/**
 * Kills a process tree to clean up the mess left by a build.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.201
 * @deprecated as of 1.315. Use {@link ProcessTree}.
 */
@Deprecated
public final class ProcessTreeKiller {
    /**
     * Short for {@code kill(proc,null)}
     *
     * @deprecated Use {@link OSProcess#killRecursively()}
     */
    @Deprecated
    public void kill(Process proc) throws InterruptedException {
        kill(proc,null);
    }

    /**
     * In addition to what {@link #kill(Process)} does, also tries to
     * kill all the daemon processes launched.
     *
     * <p>
     * Kills the given process (like {@link Process#destroy()}
     * but also attempts to kill descendant processes created from the given
     * process.
     *
     * <p>
     * In addition, optionally perform "search and destroy" based on environment
     * variables. In this method, the method is given a
     * "model environment variables", which is a list of environment variables
     * and their values that are characteristic to the launched process.
     * The implementation is expected to find processes
     * in the system that inherit these environment variables, and kill
     * them all. This is suitable for locating daemon processes
     * that cannot be tracked by the regular ancestor/descendant relationship.
     *
     * <p>
     * The implementation is obviously OS-dependent.
     *
     * @param proc
     *      Process to be killed recursively. Can be null.
     * @param modelEnvVars
     *      If non-null, search-and-destroy will be performed.
     * @deprecated Use {@link ProcessTree#killAll(Map)} and {@link OSProcess#killRecursively()}
     */
    @Deprecated
    public void kill(Process proc, Map<String, String> modelEnvVars) throws InterruptedException {
        ProcessTree pt = ProcessTree.get();
        if(proc!=null)
            pt.get(proc).killRecursively();
        if(modelEnvVars!=null)
            pt.killAll(modelEnvVars);
    }

    /**
     * Short for {@code kill(null,modelEnvVars)}
     * @deprecated Use {@link ProcessTree#killAll(Map)}
     */
    @Deprecated
    public void kill(Map<String, String> modelEnvVars) throws InterruptedException {
        kill(null,modelEnvVars);
    }

    /**
     * Creates a magic cookie that can be used as the model environment variable
     * when we later kill the processes.
     *
     * @deprecated Use {@link EnvVars#createCookie()}
     */
    @Deprecated
    public static EnvVars createCookie() {
        return EnvVars.createCookie();
    }

    /**
     * Gets the {@link ProcessTreeKiller} suitable for the current system
     * that JVM runs in, or in the worst case return the default one
     * that's not capable of killing descendants at all.
     */
    public static ProcessTreeKiller get() {
        return new ProcessTreeKiller();
    }
}
