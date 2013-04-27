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
    @Argument(index=0, required=true, usage="Name of the build variable")
    public String name;

    @Argument(index=1,required=true, usage="Value of the build variable")
    public String value;

    @Override
    public String getShortDescription() {
        return Messages.SetBuildParameterCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {
        Run r = getCurrentlyBuilding();

        StringParameterValue p = new StringParameterValue(name, value);

        ParametersAction a = r.getAction(ParametersAction.class);
        if (a!=null) {
            ParametersAction b = a.createUpdated(Collections.singleton(p));
            r.addAction(b);
            r.getActions().remove(a);
        } else {
            r.addAction(new ParametersAction(p));
        }

        return 0;
    }
}
