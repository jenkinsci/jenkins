package hudson.model.details;

import hudson.model.Run;
import jenkins.model.Detail;

/**
 * Displays the start time of the given build
 * @implNote This will render Jelly, hence the fields return null
 */
public class TimestampDetail extends Detail {

    public TimestampDetail(Run<?, ?> run) {
        super(run);
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }
}
