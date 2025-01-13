package jenkins.model.details;

import hudson.model.Run;
import jenkins.model.Detail;

/**
 * Displays the duration of the given run, or, if the run has completed, shows the total time it took to execute
 */
public class DurationDetail extends Detail {

    public DurationDetail(Run<?, ?> run) {
        super(run);
    }
}
