package hudson.model.details;

import hudson.model.Run;
import jenkins.model.Detail;

/**
 * Displays the duration of the given build, or, if the build has completed, shows the total time it took to execute
 * @implNote This will render Jelly, hence the fields return null
 */
public class DurationDetail extends Detail {

    public DurationDetail(Run<?, ?> run) {
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
