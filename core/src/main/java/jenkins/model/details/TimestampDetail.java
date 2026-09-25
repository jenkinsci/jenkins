package jenkins.model.details;

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
}
