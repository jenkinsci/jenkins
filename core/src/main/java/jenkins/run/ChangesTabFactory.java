package jenkins.run;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Tab;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewBuildPageUserExperimentalFlag;
import jenkins.scm.RunWithSCM;

@Extension(ordinal = Integer.MAX_VALUE - 2)
public class ChangesTabFactory extends TransientActionFactory<Run> {

    @Override
    public Class<Run> type() {
        return Run.class;
    }

    @NonNull
    @Override
    public Collection<? extends Tab> createFor(@NonNull Run target) {
        boolean isExperimentalUiEnabled = new NewBuildPageUserExperimentalFlag().getFlagValue();

        if (!isExperimentalUiEnabled) {
            return Collections.emptySet();
        }

        if (target instanceof RunWithSCM<?, ?> targetWithSCM) {
            var hasChangeSet = !targetWithSCM.getChangeSets().isEmpty();
            if (hasChangeSet) {
                return Collections.singleton(new ChangesTab(target));
            }
        }

        return Collections.emptySet();
    }
}
