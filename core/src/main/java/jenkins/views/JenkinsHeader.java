package jenkins.views;

import hudson.Extension;

/**
 * Default {@link Header} provided by Jenkins
 * 
 * @see Header
 */
@Extension(ordinal = Integer.MIN_VALUE)
public class JenkinsHeader extends FullHeader {
    
    @Override
    public boolean isEnabled() {
        return true;
    }

}
