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

import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.tasks.BuildWrapper;
import jenkins.model.logging.Loggable;
import jenkins.model.logging.LoggingMethod;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logging method which takes {@link OutputStream} as a destination.
 * This implementation consults with {@link ConsoleLogFilter} extensions in Jenkins.
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(Beta.class)
public abstract class StreamLoggingMethod extends LoggingMethod {

    private static final Logger LOGGER =
            Logger.getLogger(StreamLoggingMethod.class.getName());

    public StreamLoggingMethod(@Nonnull Loggable loggable) {
        super(loggable);
    }

    public abstract OutputStream createOutputStream() throws IOException;

    /**
     * Defines an additional Console Log Filter to be used with the logging method.
     * This filter may be also used for log redirection and multi-reporting in very custom cases.
     * @return Log filter. {@code null} if no custom implementation
     */
    public ConsoleLogFilter getExtraConsoleLogFilter() {
        return null;
    }

    public final StreamBuildListener createBuildListener() throws IOException, InterruptedException {

        OutputStream logger = createOutputStream();
        if (!(loggable instanceof Run<?,?>)) {
            throw new IOException("Loggable is not a Run instance: " + loggable.getClass());
        }
        Run<?,?> build = (Run<?, ?>)loggable;

        // Global log filter
        for (ConsoleLogFilter filter : ConsoleLogFilter.all()) {
            logger = filter.decorateLogger(build, logger);
        }
        final Job<?, ?> project = build.getParent();

        // Project specific log filters
        if (project instanceof BuildableItemWithBuildWrappers && build instanceof AbstractBuild) {
            BuildableItemWithBuildWrappers biwbw = (BuildableItemWithBuildWrappers) project;
            for (BuildWrapper bw : biwbw.getBuildWrappersList()) {
                logger = bw.decorateLogger((AbstractBuild) build, logger);
            }
        }

        // Decorate logger by logging method of this build
        final ConsoleLogFilter f = getExtraConsoleLogFilter();
        if (f != null) {
            LOGGER.log(Level.INFO, "Decorated run {0} by a custom log filter {1}",
                    new Object[]{this, f});
            logger = f.decorateLogger(build, logger);
        }
        return new StreamBuildListener(logger, getOwner().getCharset());
    }
}
