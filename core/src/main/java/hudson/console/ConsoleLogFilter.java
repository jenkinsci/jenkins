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
import hudson.model.AbstractBuild;
import hudson.tasks.BuildWrapper;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A hook to allow filtering of information that is written to the console log.
 * Unlike {@link ConsoleAnnotator} and {@link ConsoleNote}, this class provides
 * direct access to the underlying {@link OutputStream} so it's possible to suppress
 * data, which isn't possible from the other interfaces.
 * ({@link ArgumentListBuilder#add(String, boolean)} is a simpler way to suppress a single password.)
 * @author dty
 * @since 1.383
 * @see BuildWrapper#decorateLogger
 */
public abstract class ConsoleLogFilter implements ExtensionPoint {
    /**
     * Called on the start of each build, giving extensions a chance to intercept
     * the data that is written to the log.
     */
    public abstract OutputStream decorateLogger(AbstractBuild build, OutputStream logger) throws IOException, InterruptedException;

    /**
     * All the registered {@link ConsoleLogFilter}s.
     */
    public static ExtensionList<ConsoleLogFilter> all() {
        return ExtensionList.lookup(ConsoleLogFilter.class);
    }
}
