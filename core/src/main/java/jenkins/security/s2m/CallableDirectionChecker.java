package jenkins.security.s2m;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.remoting.Callable;
import hudson.remoting.ChannelBuilder;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.ChannelConfigurator;
import jenkins.security.Roles;
import jenkins.util.SystemProperties;
import org.jenkinsci.remoting.Role;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Inspects {@link Callable}s that run on the controller.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.587 / 1.580.1
 */
@Restricted(NoExternalUse.class) // used implicitly via listener
public class CallableDirectionChecker extends RoleChecker {

    private static final String BYPASS_PROP = CallableDirectionChecker.class.getName() + ".allow";

    /**
     * Switch to disable all the defense mechanism completely.
     *
     * This is an escape hatch in case the fix breaks something critical, to allow the user
     * to keep operation.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean BYPASS = SystemProperties.getBoolean(BYPASS_PROP);

    @Override
    public void check(RoleSensitive subject, @NonNull Collection<Role> expected) {
        final String name = subject.getClass().getName();

        if (expected.contains(Roles.MASTER)) {
            LOGGER.log(Level.FINE, "Executing {0} is allowed since it is targeted for the controller role", name);
            return;    // known to be safe
        }

        if (BYPASS) {
            LOGGER.log(Level.FINE, "Allowing {0} to be sent from agent to controller because bypass is set", name);
            return;
        }

        throw new SecurityException("Sending " + name + " from agent to controller is prohibited.\nSee https://www.jenkins.io/redirect/security-144 for more details");
    }

    /**
     * Installs {@link CallableDirectionChecker} to every channel.
     */
    @Restricted(DoNotUse.class) // impl
    @Extension
    public static class ChannelConfiguratorImpl extends ChannelConfigurator {
        @Override
        public void onChannelBuilding(ChannelBuilder builder, Object context) {
            // if the big red emergency button is pressed, then we need to disable the defense mechanism,
            // including enabling classloading.
            if (!BYPASS) {
                builder.withRemoteClassLoadingAllowed(false);
            }
            // In either of the above cases, the check method will return normally, but may log things.
            builder.withRoleChecker(new CallableDirectionChecker());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CallableDirectionChecker.class.getName());
}
