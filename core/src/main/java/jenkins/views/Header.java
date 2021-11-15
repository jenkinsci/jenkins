package jenkins.views;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * Extension point that provides capabilities to render a specific header
 * 
 * @author Ildefonso Montero
 * @see JenkinsHeader
 */
public abstract class Header implements ExtensionPoint {
    
    private static Logger LOGGER = Logger.getLogger(JenkinsHeader.class.getName());
    
    /**
     * Checks if header is enabled.
     * @return if header is enabled
     */
    abstract boolean isHeaderEnabled();

    @Restricted(NoExternalUse.class)
    @CheckForNull
    public static Header get() {
        List<Header> headers = ExtensionList.lookup(Header.class).stream().filter(header -> header.isHeaderEnabled()).collect(Collectors.toList());
        if (!headers.isEmpty()) {
            if (headers.size() > 1) {
                LOGGER.warning("More than one configured header. This should not happen. Serving the Jenkins default header and please review");
            } else {
                return headers.get(0);
            }
        }
        return new JenkinsHeader();
    }
}
