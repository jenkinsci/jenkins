package jenkins.security;

import hudson.Extension;
import hudson.remoting.Callable;
import hudson.remoting.CallableDecorator;
import hudson.remoting.ChannelBuilder;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rejects {@link MasterToSlave} callables.
 *
 * @author Kohsuke Kawaguchi
 * @since TODO
 */
public class CallableDirectionChecker extends CallableDecorator {

    private static final String BYPASS_PROP = "jenkins.security.CallableDirectionChecker.allowUnmarkedCallables";

    private final SlaveComputer computer;

    public CallableDirectionChecker(SlaveComputer computer) {
        this.computer = computer;
    }

    @Override
    public <V, T extends Throwable> Callable<V, T> userRequest(Callable<V, T> op, Callable<V, T> stem) {
        Class<?> c = op.getClass();
        String name = c.getName();

        if (name.startsWith("hudson.remoting")) { // TODO probably insecure
            return stem;    // lower level services provided by remoting, such IOSyncer, RPCRequest, Ping, etc. that we allow
        }

        if (c.isAnnotationPresent(SlaveToMaster.class)) {
            return stem;    // known to be safe
        }

        String node = computer.getName();
        if (c.isAnnotationPresent(MasterToSlave.class)) {
            throw new SecurityException("Sending " + name + " from " + node + " to master is prohibited");
        } else {
            // No annotation provided, so we do not know whether it is safe or not.
            if (Boolean.getBoolean(BYPASS_PROP)) {
                LOGGER.log(Level.FINE, "Allowing {0} to be sent from {1} to master", new Object[] {name, node});
                return stem;
            } else if (Boolean.getBoolean(BYPASS_PROP + "." + name)) {
                LOGGER.log(Level.FINE, "Explicitly allowing {0} to be sent from {1} to master", new Object[] {name, node});
                return stem;
            } else {
                throw new SecurityException("Sending from " + node + " to master is prohibited unless you run with: -D" + BYPASS_PROP + "." + name);
            }
        }
    }

    /**
     * Installs {@link CallableDirectionChecker} to every channel.
     */
    @Extension
    public static class ComputerListenerImpl extends ComputerListener {
        @Override
        public void onChannelBuilding(ChannelBuilder builder, SlaveComputer sc) {
            builder.with(new CallableDirectionChecker(sc));
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CallableDirectionChecker.class.getName());
}
