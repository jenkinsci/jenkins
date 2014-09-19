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
    private final SlaveComputer computer;

    public CallableDirectionChecker(SlaveComputer computer) {
        this.computer = computer;
    }

    @Override
    public <V, T extends Throwable> Callable<V, T> userRequest(Callable<V, T> op, Callable<V, T> stem) {
        Class<?> c = op.getClass();

        if (c.getName().startsWith("hudson.remoting")) // TODO probably insecure
            return stem;    // lower level services provided by remoting, such IOSyncer, RPCRequest, Ping, etc. that we allow

        if (c.isAnnotationPresent(SlaveToMaster.class)) {
            return stem;    // known to be safe
        }

        if (c.isAnnotationPresent(MasterToSlave.class)) {
            throw new SecurityException(String.format("Invocation of %s is prohibited", c));
        } else {
            // no annotation provided, so we don't know.
            // to err on the correctness we'd let it pass with reporting, which
            // provides auditing trail.
            LOGGER.log(Level.WARNING, "Unchecked callable from {0}: {1}", new Object[] {computer.getName(), c});
            return stem;
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
