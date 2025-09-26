package jenkins.run;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Run;
import java.util.Collection;
import java.util.Collections;
import jenkins.console.DefaultConsoleUrlProvider;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewBuildPageUserExperimentalFlag;

@Extension(ordinal = Integer.MAX_VALUE - 1)
public class ConsoleTabFactory extends TransientActionFactory<Run> {

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @NonNull
    @Override
    public Collection<? extends Tab> createFor(@NonNull Run target) {
        var consoleProvider = Functions.getConsoleProviderFor(target);
        boolean isExperimentalUiEnabled = new NewBuildPageUserExperimentalFlag().getFlagValue();

        if (!consoleProvider.getClass().equals(DefaultConsoleUrlProvider.class) || !isExperimentalUiEnabled) {
            return Collections.emptySet();
        }

        return Collections.singleton(new ConsoleTab(target));
    }
}
