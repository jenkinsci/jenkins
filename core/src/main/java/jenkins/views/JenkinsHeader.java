package jenkins.views;

import hudson.Extension;

/**
 * Default {@link Header} provided by Jenkins
 * 
 * @author Ildefonso Montero
 * @see Header
 */
@Extension(ordinal = Integer.MIN_VALUE)
public class JenkinsHeader extends Header {
    
    @Override
    public boolean isHeaderEnabled() {
        return true;
    }

}
