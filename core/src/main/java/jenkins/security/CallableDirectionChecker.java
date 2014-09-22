package jenkins.security;

import hudson.Extension;
import hudson.remoting.Callable;
import hudson.remoting.CallableDecorator;
import hudson.remoting.ChannelBuilder;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Rejects non-{@link SlaveToMaster} callables.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class) // used implicitly via listener
public class CallableDirectionChecker extends CallableDecorator {

    private static final String BYPASS_PROP = "jenkins.security.CallableDirectionChecker.allowUnmarkedCallables";

    private CallableDirectionChecker() {}

    @Override
    public <V, T extends Throwable> Callable<V, T> userRequest(Callable<V, T> op, Callable<V, T> stem) {
        Class<?> c = op.getClass();
        String name = c.getName();

        if (name.startsWith("hudson.remoting")) { // TODO probably insecure
            LOGGER.log(Level.FINE, "Sending {0} to master is allowed since it is in remoting", name);
            return stem;    // lower level services provided by remoting, such IOSyncer, RPCRequest, Ping, etc. that we allow
        }

        if (c.isAnnotationPresent(SlaveToMaster.class)) {
            LOGGER.log(Level.FINE, "Sending {0} is allowed since it is marked @SlaveToMaster", name);
            return stem;    // known to be safe
        }

        if (c.isAnnotationPresent(MasterToSlave.class)) {
            throw new SecurityException("Sending " + name + " from slave to master is prohibited");
        } else {
            // No annotation provided, so we do not know whether it is safe or not.
            if (Boolean.getBoolean(BYPASS_PROP)) {
                LOGGER.log(Level.FINE, "Allowing {0} to be sent from slave to master", name);
                return stem;
            } else if (Boolean.getBoolean(BYPASS_PROP + "." + name)) {
                LOGGER.log(Level.FINE, "Explicitly allowing {0} to be sent from slave to master", name);
                return stem;
            } else {
                throw new SecurityException("Sending from slave to master is prohibited unless you run with: -D" + BYPASS_PROP + "." + name);
            }
        }
    }

    /**
     * Installs {@link CallableDirectionChecker} to every channel.
     */
    @Restricted(DoNotUse.class) // impl
    @Extension
    public static class ComputerListenerImpl extends ComputerListener {
        @Override
        public void onChannelBuilding(ChannelBuilder builder, SlaveComputer sc) {
            builder.with(new CallableDirectionChecker());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CallableDirectionChecker.class.getName());
}
