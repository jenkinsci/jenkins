/*
 *  The MIT License
 * 
 *  Copyright 2010 Yahoo! Inc.
 * 
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 * 
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 * 
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package hudson.console;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.util.ArgumentListBuilder;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import jenkins.util.JenkinsJVM;

/**
 * A hook to allow filtering of information that is written to the console log.
 * Unlike {@link ConsoleAnnotator} and {@link ConsoleNote}, this class provides
 * direct access to the underlying {@link OutputStream} so it's possible to suppress
 * data, which isn't possible from the other interfaces.
 * ({@link ArgumentListBuilder#add(String, boolean)} is a simpler way to suppress a single password.)
 * <p>Implementations which are {@link Serializable} may be sent to an agent JVM for processing.
 * In particular, this happens under <a href="https://jenkins.io/jep/210">JEP-210</a>.
 * In this case, the implementation should not assume that {@link JenkinsJVM#isJenkinsJVM},
 * and if generating {@link ConsoleNote}s will need to encode them on the master side first.
 * @author dty
 * @since 1.383
 * @see BuildWrapper#decorateLogger
 */
public abstract class ConsoleLogFilter implements ExtensionPoint {
    /**
     * Called on the start of each build, giving extensions a chance to intercept
     * the data that is written to the log.
     *
     * @throws AbstractMethodError
     *     when a plugin overrides neither this method nor {@link #decorateLogger(Run, OutputStream)}.
     *
     * @deprecated as of 1.632. Use {@link #decorateLogger(Run, OutputStream)}
     */
    @Deprecated
    public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) throws IOException, InterruptedException {
        if (Util.isOverridden(ConsoleLogFilter.class, getClass(), "decorateLogger", Run.class, OutputStream.class)) {
            // old client calling newer implementation. forward the call.
            return decorateLogger((Run) build, logger);
        } else {
            // happens only if the subtype fails to override either decorateLogger method
            throw new AbstractMethodError("Plugin class '" + this.getClass().getName() + "' does not override " +
                    "either overload of the decorateLogger method.");
        }
    }

    /**
     * Called on the start of each build, giving extensions a chance to intercept
     * the data that is written to the log.
     *
     * <p>
     * Even though this method is not marked 'abstract', this is the method that must be overridden
     * by extensions.
     */
    public OutputStream decorateLogger(Run build, OutputStream logger) throws IOException, InterruptedException {
        // this implementation is backward compatibility thunk in case subtypes only override the
        // old signature (AbstractBuild,OutputStream)

        if (build instanceof AbstractBuild) {
            // maybe the plugin implements the old signature.
            return decorateLogger((AbstractBuild) build, logger);
        } else {
            // this ConsoleLogFilter can only decorate AbstractBuild, so just pass through
            return logger;
        }
    }

    /**
     * Called to decorate logger for master/agent communication.
     *
     * @param computer
     *      Agent computer for which the logger is getting decorated. Useful to do
     *      contextual decoration.
     * @since 1.632
     */
    public OutputStream decorateLogger(@NonNull Computer computer, OutputStream logger) throws IOException, InterruptedException {
        return logger;      // by default no-op
    }

    /**
     * All the registered {@link ConsoleLogFilter}s.
     */
    public static ExtensionList<ConsoleLogFilter> all() {
        return ExtensionList.lookup(ConsoleLogFilter.class);
    }
}
