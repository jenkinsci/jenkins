package jenkins.views;

import hudson.Extension;
import jenkins.model.experimentalflags.UserExperimentalFlag;

/**
 * Experimental {@link Header} provided by Jenkins
 *
 * @see Header
 */
@Extension(ordinal = Integer.MIN_VALUE)
public class ExperimentalJenkinsHeader extends FullHeader {

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return UserExperimentalFlag.getFlagValueForCurrentUser("jenkins.model.experimentalflags.NewHeaderUserExperimentalFlag");
    }
}
