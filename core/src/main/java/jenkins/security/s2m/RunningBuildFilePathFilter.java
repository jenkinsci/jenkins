/*
 * The MIT License
 *
 * Copyright 2021 CloudBees, Inc.
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

package jenkins.security.s2m;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.remoting.ChannelBuilder;
import jenkins.ReflectiveFilePathFilter;
import jenkins.model.Jenkins;
import jenkins.security.ChannelConfigurator;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * When an agent tries to access build directories on the controller, limit it to those for builds running on that agent.
 *
 * @since 2.319
 */
@Restricted(NoExternalUse.class)
public class RunningBuildFilePathFilter extends ReflectiveFilePathFilter {

    /**
     * By default, unauthorized accesses will result in a {@link SecurityException}.
     * If this is set to {@code false}, instead just log a warning.
     */
    private static final String FAIL_PROPERTY = RunningBuildFilePathFilter.class.getName() + ".FAIL";

    /**
     * Disables this filter entirely.
     */
    private static final String SKIP_PROPERTY = RunningBuildFilePathFilter.class.getName() + ".SKIP";

    private static final Logger LOGGER = Logger.getLogger(RunningBuildFilePathFilter.class.getName());

    private final Object context;

    public RunningBuildFilePathFilter(Object context) {
        this.context = context;
    }

    @Override
    protected boolean op(String name, File path) throws SecurityException {
        if (SystemProperties.getBoolean(SKIP_PROPERTY)) {
            LOGGER.log(Level.FINE, () -> "Skipping check for '" + name + "' on '" + path + "'");
            return false;
        }

        final Jenkins jenkins = Jenkins.get();

        String patternString;
        try {
            patternString = Jenkins.expandVariablesForDirectory(jenkins.getRawBuildsDir(), "(.+)", "\\Q" + Jenkins.get().getRootDir().getCanonicalPath().replace('\\', '/') + "\\E/jobs/(.+)") + "/[0-9]+(/.*)?";
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to obtain canonical path to Jenkins home directory", e);
            throw new SecurityException("Failed to obtain canonical path"); // Minimal details
        }
        final Pattern pattern = Pattern.compile(patternString);

        String absolutePath;
        try {
            absolutePath = path.getCanonicalPath().replace('\\', '/');
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to obtain canonical path to '" + path + "'", e);
            throw new SecurityException("Failed to obtain canonical path"); // Minimal details
        }
        if (!pattern.matcher(absolutePath).matches()) {
            /* This is not a build directory, so another filter will take care of it */
            LOGGER.log(Level.FINE, "Not a build directory, so skipping: " + absolutePath);
            return false;
        }

        if (!(context instanceof Computer)) {
            LOGGER.warning(() -> "Unrecognized context " + context + " rejected for " + name + " on " + path);
            throw new SecurityException("Failed to discover context of access to build directory"); // Minimal details
        }
        Computer c = (Computer) context;
        final Path thePath = path.getAbsoluteFile().toPath();
        for (Executor executor : c.getExecutors()) {
            Run<?, ?> build = findRun(executor.getCurrentExecutable());
            if (build == null) {
                continue;
            }
            final Path buildDir = build.getRootDir().getAbsoluteFile().toPath();
            // If the directory being accessed is for a build currently running on this node, allow it
            if (thePath.startsWith(buildDir)) {
                return false;
            }
        }

        final String computerName = c.getName();
        if (SystemProperties.getBoolean(FAIL_PROPERTY, true)) {
            // This filter can only prohibit by throwing a SecurityException; it never allows on its own.
            LOGGER.log(Level.WARNING, "Rejecting unexpected agent-to-controller file path access: Agent '" + computerName + "' is attempting to access '" + absolutePath + "' using operation '" + name + "'. Learn more: https://www.jenkins.io/redirect/security-144/");
            throw new SecurityException("Agent tried to access build directory of a build not currently running on this system. Learn more: https://www.jenkins.io/redirect/security-144/");
        } else {
            LOGGER.log(Level.WARNING, "Unexpected agent-to-controller file path access: Agent '" + computerName + "' is accessing '" + absolutePath + "' using operation '" + name + "'. Learn more: https://www.jenkins.io/redirect/security-144/");
            return false;
        }
    }

    private static @CheckForNull Run<?, ?> findRun(@CheckForNull Queue.Executable exec) {
        if (exec == null) {
            return null;
        } else if (exec instanceof Run) {
            return (Run) exec;
        } else {
            return findRun(exec.getParentExecutable());
        }
    }

    @Extension
    public static class ChannelConfiguratorImpl extends ChannelConfigurator {
        @Override
        public void onChannelBuilding(ChannelBuilder builder, @Nullable Object context) {
            new RunningBuildFilePathFilter(context).installTo(builder, 150.0);
        }
    }
}
