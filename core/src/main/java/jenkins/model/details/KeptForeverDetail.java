package jenkins.model.details;

import hudson.model.Run;
import org.jspecify.annotations.Nullable;

/**
 * Displays if the build is marked to be kept forever.
 * Only shown if the build is kept forever.
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
