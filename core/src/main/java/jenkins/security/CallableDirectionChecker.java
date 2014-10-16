package jenkins.security;

import hudson.Extension;
import hudson.remoting.Callable;
import hudson.remoting.ChannelBuilder;
import org.jenkinsci.remoting.Role;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inspects {@link Callable}s that run on the master.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
@Restricted(NoExternalUse.class) // used implicitly via listener
public class CallableDirectionChecker extends RoleChecker {
    /**
     * Context parameter given to {@link ChannelConfigurator#onChannelBuilding(ChannelBuilder, Object)}.
     */
    private final Object context;

    private static final String BYPASS_PROP = CallableDirectionChecker.class.getName()+".allow";

    /**
     * Switch to disable all the defense mechanism completely.
     *
     * This is an escape hatch in case the fix breaks something critical, to allow the user
     * to keep operation.
     */
    public static boolean BYPASS = Boolean.getBoolean(BYPASS_PROP);

    /**
     * Whitelist of {@link RoleSensitive} class names (mostly {@link Callable} classes) that are allowed
     * to pass through.
     */
    public static ConcurrentMap<String,Void> BYPASS_CALLABLES = new ConcurrentHashMap<String,Void>();

    /**
     * Test feature that is meant to replace all logging, and log what would have been violations.
     * TODO delete before release
     */
    private static final PrintWriter BYPASS_LOG;

    static {
        String log = System.getProperty(CallableDirectionChecker.class.getName()+".log");
        if (log == null) {
            BYPASS_LOG = null;
        } else {
            try {
                BYPASS_LOG = new PrintWriter(new OutputStreamWriter(new FileOutputStream(log, true)), true);
            } catch (FileNotFoundException x) {
                throw new ExceptionInInitializerError(x);
            }
        }
    }

    private CallableDirectionChecker(Object context) {
        this.context = context;
    }

    @Override
    public void check(RoleSensitive subject, @Nonnull Collection<Role> expected) throws SecurityException {
        final String name = subject.getClass().getName();

        if (expected.contains(Roles.MASTER)) {
            if (BYPASS_LOG == null) {
                LOGGER.log(Level.FINE, "Executing {0} is allowed since it is targeted for the master role", name);
            }
            return;    // known to be safe
        }

        if (BYPASS_LOG != null) {
            BYPASS_LOG.println(name);
            return;
        }

        if (isWhitelisted(subject,expected)) {
            // this subject is dubious, but we are letting it through as per whitelisting
            LOGGER.log(Level.FINE, "Explicitly allowing {0} to be sent from slave to master", name);
            return;
        }

        throw new SecurityException("Sending " + name + " from slave to master is prohibited. See http://jenkins-ci.org/security-144 for more details");
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
            if (!BYPASS && BYPASS_LOG == null) {
                builder.withRemoteClassLoadingAllowed(false);
            }
            // In either of the above cases, the check method will return normally, but may log things.
            builder.withRoleChecker(new CallableDirectionChecker(context));
        }
    }

    /**
     * Whitelist rule based on system properties.
     */
    @Extension
    public static class DefaultWhitelist extends CallableWhitelist {
        @Override
        public boolean isWhitelisted(RoleSensitive subject, Collection<Role> expected, Object context) {
            final String name = subject.getClass().getName();

            if (BYPASS) // everything is whitelisted
                return true;

            if (BYPASS_CALLABLES.containsKey(name))
                return true;

            if (Boolean.getBoolean(BYPASS_PROP+"."+name)) {
                BYPASS_CALLABLES.put(name,null);
                return true;
            }

            return false;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CallableDirectionChecker.class.getName());
}
