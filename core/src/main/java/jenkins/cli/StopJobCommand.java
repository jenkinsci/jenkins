/*
 * The MIT License
 *
 * Copyright (c) 2018, Ilia Zasimov
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
package jenkins.cli;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class StopJobCommand extends CLICommand {

    private static final Logger LOGGER = Logger.getLogger(StopJobCommand.class.getName());

    @VisibleForTesting
    @Argument(usage = "Name of the job(s) to stop", required = true, multiValued = true)
    /*private */ List<String> jobNames;

    @Override
    public String getShortDescription() {
        return "Stop all running builds for job(s)";
    }

    @Override
    protected int run() throws Exception {
        Jenkins jenkins = Jenkins.get();
        final HashSet<String> names = new HashSet<>();
        names.addAll(jobNames);
        final StringBuilder resultBuilder = new StringBuilder();
        for (final String jobName : names) {
            Item item = jenkins.getItemByFullName(jobName);
            if (item instanceof AbstractProject) {
                stopJobBuilds((AbstractProject) item, jobName, resultBuilder);
            } else if (item != null) {
                resultBuilder.append("Job have not supported type.\n");
            } else {
                resultBuilder.append(String.format("Job with name %s not found.\n", jobName));
            }
        }

        stdout.print(resultBuilder.toString());
        return 0;
    }

    private void stopJobBuilds(final AbstractProject job,
                               final String jobName,
                               final StringBuilder resultBuilder) throws IOException, ServletException {
        final AbstractBuild lastBuild = job.getLastBuild();
        final List<String> stoppedBuildsNames = new ArrayList<>();
        if (lastBuild != null) {
            stopBuild(lastBuild, jobName, stoppedBuildsNames);
            checkAndStopPreviousBuilds(lastBuild, jobName, stoppedBuildsNames);
        }
        updateResultOutput(resultBuilder, jobName, stoppedBuildsNames);
    }

    private void stopBuild(final AbstractBuild build,
                           final String jobName,
                           final List<String> stoppedBuildNames) throws IOException, ServletException {
        if (build.isBuilding()) {
            final String buildName = build.getDisplayName();
            stoppedBuildNames.add(buildName);
            build.doStop();
            logBuildStopped(jobName, buildName);
        }
    }

    private void checkAndStopPreviousBuilds(final AbstractBuild lastBuild,
                                            final String jobName,
                                            final List<String> stoppedBuildsNames) throws IOException, ServletException {
        Run build = lastBuild.getPreviousBuildInProgress();
        while (build instanceof AbstractBuild) {
            stopBuild((AbstractBuild) build, jobName, stoppedBuildsNames);
            build = build.getPreviousBuildInProgress();
        }
    }

    private void updateResultOutput(final StringBuilder result,
                                    final String jobName,
                                    final List<String> stoppedBuildNames) {
        if (stoppedBuildNames.isEmpty()) {
            result.append(String.format("No builds stopped for job '%s'", jobName));
        } else {
            result.append(String.format("Builds stopped for job '%s': ", jobName));
            for (String buildName : stoppedBuildNames) {
                result.append(buildName).append("; ");
            }
        }
        result.append("\n");
    }

    private void logBuildStopped(final String jobName, final String buildName) {
        LOGGER.log(Level.INFO, String.format("Build %s in job %s aborted", buildName, jobName));
    }

}
