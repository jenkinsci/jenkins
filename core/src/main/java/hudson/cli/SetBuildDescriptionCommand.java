package hudson.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import java.io.Serializable;
import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.Argument;

// TODO: Remove Serializable
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "The Serializable should be removed.")
@Extension
public class SetBuildDescriptionCommand extends CLICommand implements Serializable {

    @Override
    public String getShortDescription() {
        return Messages.SetBuildDescriptionCommand_ShortDescription();
     }

    @Argument(metaVar = "JOB", usage = "Name of the job to build", required = true, index = 0)
    public transient Job<?, ?> job;

    @Argument(metaVar = "BUILD#", usage = "Number of the build", required = true, index = 1)
    public int number;

    @Argument(metaVar = "DESCRIPTION", required = true, usage = "Description to be set. '=' to read from stdin.", index = 2)
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public String description;

    @Override
    protected int run() throws Exception {
        Run run = job.getBuildByNumber(number);
        if (run == null)
            throw new IllegalArgumentException("No such build #" + number);

        run.checkPermission(Run.UPDATE);

        if ("=".equals(description)) {
            description = IOUtils.toString(stdin);
        }

        run.setDescription(description);

        return 0;
    }

}
