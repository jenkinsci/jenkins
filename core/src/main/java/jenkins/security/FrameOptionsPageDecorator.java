package jenkins.security;

import hudson.Extension;
import hudson.SystemProperties;
import hudson.model.PageDecorator;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Adds the 'X-Frame-Options' header to all web pages.
 *
 * @since 1.581
 */
@Extension(ordinal = 1000)
public class FrameOptionsPageDecorator extends PageDecorator {
    @Restricted(NoExternalUse.class)
    public static boolean enabled = Boolean.valueOf(SystemProperties.getProperty(FrameOptionsPageDecorator.class.getName() + ".enabled", "true"));
}
