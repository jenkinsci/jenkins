package jenkins.views;

import java.util.Optional;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
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
     * Checks if header is enabled.
     * @return if header is enabled
     */
    public abstract boolean isEnabled();

    @Restricted(NoExternalUse.class)
    public static Header get() {
        Optional<Header> header = ExtensionList.lookup(Header.class).stream().filter(Header::isEnabled).findFirst();
        return header.orElseGet(() -> new JenkinsHeader());
    }
}
