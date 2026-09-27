package jenkins.model.details;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;

/**
 * Displays the start time of the given run
 */
public class TimestampDetail extends Detail {

    public TimestampDetail(Run<?, ?> run) {
        super(run);
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public @NonNull Visibility getVisibility() {
        return Visibility.BOTH;
    }
}
