package jenkins.model.details;

import hudson.model.Run;
import org.jspecify.annotations.Nullable;

/**
 * Displays the duration of the given run, or, if the run has completed, shows the total time it took to execute
 */
public class KeptForeverDetail extends Detail {

    public KeptForeverDetail(Run<?, ?> run) {
        super(run);
    }

    @Override
    public @Nullable String getDisplayName() {
        return Messages.KeptForeverDetail_DisplayName();
    }

    @Override
    public @Nullable String getIconClassName() {
        Run<?, ?> run = (Run<?, ?>) getObject();
        return run.isKeepLog() ? "symbol-lock-closed" : null;
    }
}
