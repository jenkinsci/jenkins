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
 * Inspects {@link Callable}s that run on the master.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.587 / 1.580.1
 */
@Restricted(NoExternalUse.class) // used implicitly via listener
public class CallableDirectionChecker extends RoleChecker {
    /**
     * Context parameter given to {@link ChannelConfigurator#onChannelBuilding(ChannelBuilder, Object)}.
     */
    private final Object context;

    private static final String BYPASS_PROP = CallableDirectionChecker.class.getName()+".allow";

    private static final String ALLOW_ANY_ROLE_PROP = CallableDirectionChecker.class.getName()+".allowAnyRole";

    /**
     * Switch to disable all the defense mechanism completely.
     *
     * This is an escape hatch in case the fix breaks something critical, to allow the user
     * to keep operation.
     */
    @SuppressFBWarnings("MS_SHOULD_BE_FINAL")
    public static boolean BYPASS = SystemProperties.getBoolean(BYPASS_PROP);

    private CallableDirectionChecker(Object context) {
        this.context = context;
    }

    @Override
    public void check(RoleSensitive subject, @NonNull Collection<Role> expected) throws SecurityException {
        final String name = subject.getClass().getName();

        if (expected.contains(Roles.MASTER)) {
            LOGGER.log(Level.FINE, "Executing {0} is allowed since it is targeted for the controller role", name);
            return;    // known to be safe
        }

        if (expected.isEmpty() && SystemProperties.getBoolean(ALLOW_ANY_ROLE_PROP, true)) {
            // TODO Is this even something we want to support, or should all infrastructure callables be exempted from the required role check?
            LOGGER.log(Level.FINE, "Executing {0} is allowed since it is targeted for any role", name);
            return;
        }

        if (isWhitelisted(subject,expected)) {
            // this subject is dubious, but we are letting it through as per whitelisting
            LOGGER.log(Level.FINE, "Explicitly allowing {0} to be sent from agent to controller", name);
            return;
        }

        throw new SecurityException("Sending " + name + " from agent to controller is prohibited.\nSee https://www.jenkins.io/redirect/security-144 for more details");
    }

    /**
     * Is this subject class name whitelisted?
     */
    private boolean isWhitelisted(RoleSensitive subject, Collection<Role> expected) {
        for (CallableWhitelist w : CallableWhitelist.all()) {
            if (w.isWhitelisted(subject, expected, context))
                return true;
        }
        return false;
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
            builder.withRoleChecker(new CallableDirectionChecker(context));
        }
    }

    /**
     * Whitelist rule based on system properties.
     *
     * For the bypass "kill" switch to be effective, it needs to have a high enough priority
     */
    @Extension(ordinal=100)
    public static class DefaultWhitelist extends CallableWhitelist {
        @Override
        public boolean isWhitelisted(RoleSensitive subject, Collection<Role> expected, Object context) {
            return BYPASS;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CallableDirectionChecker.class.getName());
}
