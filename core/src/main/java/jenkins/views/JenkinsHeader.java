package jenkins.views;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.ExtensionList;

/**
 * Default {@link Header} provided by Jenkins
 * 
 * @author Ildefonso Montero
 * @see Header
 */
@Extension(ordinal = Integer.MIN_VALUE)
public class JenkinsHeader implements Header {
    
    private static Logger LOGGER = Logger.getLogger(JenkinsHeader.class.getName());

    @Override
    public boolean isHeaderEnabled() {
        return true;
    }
    
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
