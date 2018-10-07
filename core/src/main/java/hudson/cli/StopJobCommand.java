package hudson.cli;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
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
            AbstractProject job = null;
            Item item = jenkins.getItemByFullName(jobName);
            if (item instanceof AbstractProject) {
                job = (AbstractProject) item;
            }

            if (job != null) {
                stopJobBuilds(job, jobName, resultBuilder);
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
