package hudson;

import hudson.init.Initializer;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.logging.Logger;

import static hudson.init.InitMilestone.COMPLETED;

/**
 * Registers a DNS multi-cast service-discovery support.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated No longer does anything.
 */
@Deprecated
@Restricted(NoExternalUse.class)
public class DNSMultiCast {
    private static final Logger logger = Logger.getLogger(DNSMultiCast.class.getName());

    public static boolean disabled = SystemProperties.getBoolean(DNSMultiCast.class.getName()+".disabled", true);

    @Initializer(before=COMPLETED)
    public void warn() {
        if (!disabled) {
            logger.warning("DNS multicast capability has been removed from Jenkins.");
        }
    }

}
