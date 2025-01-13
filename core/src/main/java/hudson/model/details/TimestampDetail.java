package hudson.model.details;

import hudson.model.Run;
import jenkins.model.Detail;

/**
 * Displays the start time of the given run
 * @implNote This renders Jelly
 */
public class TimestampDetail extends Detail {

    public TimestampDetail(Run<?, ?> run) {
        super(run);
    }
}
