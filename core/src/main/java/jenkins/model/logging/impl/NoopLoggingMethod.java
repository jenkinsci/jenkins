/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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
package jenkins.model.logging.impl;

import hudson.model.BuildListener;
import hudson.model.TaskListener;
import jenkins.model.logging.LogBrowser;
import jenkins.model.logging.Loggable;
import jenkins.model.logging.LoggingMethod;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Default Logging Method implementation which does nothing
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(Beta.class)
public class NoopLoggingMethod extends LoggingMethod {

    public NoopLoggingMethod(Loggable loggable) {
        super(loggable);
    }

    @CheckForNull
    @Override
    public TaskListener createTaskListener() {
        return TaskListener.NULL;
    }

    @Nonnull
    @Override
    public BuildListener createBuildListener() throws IOException, InterruptedException {
        return new BuildListener() {
            @Nonnull
            @Override
            public PrintStream getLogger() {
                return TaskListener.NULL.getLogger();
            }
        };
    }

    @Override
    public LogBrowser getDefaultLogBrowser() {
        return new NoopLogBrowser(getOwner());
    }
}
