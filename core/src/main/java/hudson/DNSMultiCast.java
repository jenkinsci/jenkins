package hudson;

import static hudson.init.InitMilestone.COMPLETED;

import hudson.init.Initializer;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Registers a DNS multi-cast service-discovery support.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated No longer does anything.
 */
@Deprecated
@Restricted(NoExternalUse.class)
public class DNSMultiCast {
    private static final Logger LOGGER = Logger.getLogger(DNSMultiCast.class.getName());

    public static boolean disabled = SystemProperties.getBoolean(DNSMultiCast.class.getName()+".disabled", true);

    @Initializer(before=COMPLETED)
    public static void warn() {
        if (!disabled) {
            LOGGER.warning("DNS multicast capability has been removed from Jenkins. More information: https://www.jenkins.io/redirect/dns-multicast");
        }
    }

}
