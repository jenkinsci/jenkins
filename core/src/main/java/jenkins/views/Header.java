package jenkins.views;

import java.util.Optional;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * Extension point that provides capabilities to render a specific header
 * 
 * @see JenkinsHeader
 * @since TODO
 */
public abstract class Header implements ExtensionPoint {
    
    /**
     * Checks if header is available
     * @return if header is available
     */
    public boolean isAvailable() {
        return isCompatible() && isEnabled();
    }
    
    /**
     * Checks API compatibility of the header
     * @return if header is compatible
     */
    public abstract boolean isCompatible();

    /**
     * Checks if header is enabled.
     * @return if header is enabled
     */
    public abstract boolean isEnabled();
    
    @Restricted(NoExternalUse.class)
    public static Header get() {
        Optional<Header> header = ExtensionList.lookup(Header.class).stream().filter(Header::isAvailable).findFirst();
        return header.orElseGet(() -> new JenkinsHeader());
    }
    
}
