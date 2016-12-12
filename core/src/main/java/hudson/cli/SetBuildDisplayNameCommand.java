package hudson.cli;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Run;
import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.Argument;

import java.io.Serializable;

@Extension
public class SetBuildDisplayNameCommand extends CLICommand implements Serializable {
    private static final long serialVersionUID = 6665171784136358536L;

    @Override
    public String getShortDescription() {
        return Messages.SetBuildDisplayNameCommand_ShortDescription();
    }

    @Argument(metaVar="JOB", usage="Name of the job to build", required=true, index=0)
    public transient AbstractProject<?, ?> job;

    @Argument(metaVar="BUILD#", usage="Number of the build", required=true, index=1)
    public int number;

    @Argument(metaVar="DISPLAYNAME", required=true, usage="DisplayName to be set. '-' to read from stdin.", index=2)
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
