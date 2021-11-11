package jenkins.views;

import hudson.ExtensionPoint;

/**
 * Extension point that provides capabilities to render a specific header
 * 
 * @author Ildefonso Montero
 * @see JenkinsHeader
 */
public interface Header extends ExtensionPoint {
    
    /**
     * Checks if header is enabled.
     * @return if header is enabled
     */
    boolean isHeaderEnabled();
}
