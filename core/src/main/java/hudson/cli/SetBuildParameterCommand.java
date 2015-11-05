package hudson.cli;

import hudson.Extension;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import org.kohsuke.args4j.Argument;

import java.util.Collections;

/**
 * Used from the build to update the build variable.
 *
 * This allows one build step to affect the environment variables seen by later build steps.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.514
 */
@Extension
public class SetBuildParameterCommand extends CommandDuringBuild {
    @Argument(index=0, metaVar="NAME", required=true, usage="Name of the build variable")
    public String name;

    @Argument(index=1, metaVar="VALUE", required=true, usage="Value of the build variable")
    public String value;

    @Override
    public String getShortDescription() {
        return Messages.SetBuildParameterCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {
        Run r = getCurrentlyBuilding();
        r.checkPermission(Run.UPDATE);

        StringParameterValue p = new StringParameterValue(name, value);

        ParametersAction a = r.getAction(ParametersAction.class);
        if (a!=null) {
            r.replaceAction(a.createUpdated(Collections.singleton(p)));
        } else {
            r.addAction(new ParametersAction(p));
        }

        return 0;
    }
}
