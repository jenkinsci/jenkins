package jenkins.views;

import hudson.ExtensionPoint;

public interface Header extends ExtensionPoint {
    
    /**
     * Checks if header is enabled. By default it is if installed, but the logic is deferred in the plugins.
     * @return
     */
    boolean isHeaderEnabled();
 
}
