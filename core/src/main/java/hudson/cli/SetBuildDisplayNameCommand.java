package hudson.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import java.io.Serializable;
import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.Argument;

@Extension
public class SetBuildDisplayNameCommand extends CLICommand implements Serializable {
    private static final long serialVersionUID = 6665171784136358536L;

    @Override
    public String getShortDescription() {
        return Messages.SetBuildDisplayNameCommand_ShortDescription();
    }

    @Argument(metaVar = "JOB", usage = "Name of the job to build", required = true, index = 0)
    public transient Job<?, ?> job;

    @Argument(metaVar = "BUILD#", usage = "Number of the build", required = true, index = 1)
    public int number;

    @Argument(metaVar = "DISPLAYNAME", required = true, usage = "DisplayName to be set. '-' to read from stdin.", index = 2)
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public String displayName;

    @Override
    protected int run() throws Exception {
        Run<?, ?> run = job.getBuildByNumber(number);
        if (run == null) {
            throw new IllegalArgumentException("Build #" + number + " does not exist");
        }
        run.checkPermission(Run.UPDATE);

        if ("-".equals(displayName)) {
            displayName = IOUtils.toString(stdin);
        }

        run.setDisplayName(displayName);

        return 0;
    }
}
