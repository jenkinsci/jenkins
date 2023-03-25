package jenkins.slaves.restarter;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Engine;
import hudson.remoting.EngineListener;
import hudson.remoting.EngineListenerAdapter;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import jenkins.security.MasterToSlaveCallable;

/**
 * Actual agent restart logic.
 *
 * <p>
 *     Use {@link ComputerListener} to install {@link EngineListener} on {@link hudson.model.Computer} instances tied to {@link DumbSlave},
 *     which in turn gets executed when the agent gets disconnected.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class JnlpSlaveRestarterInstaller extends ComputerListener implements Serializable {
    /**
     * To force installer to run on all agents, set this system property to true.
     */
    private static final boolean FORCE_INSTALL = Boolean.getBoolean(JnlpSlaveRestarterInstaller.class.getName() + ".forceInstall");

    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", justification = "method signature does not permit plumbing through the return value")
    @Override
    public void onOnline(final Computer c, final TaskListener listener) throws IOException, InterruptedException {
        if (FORCE_INSTALL || c.getNode() instanceof DumbSlave) {
            Computer.threadPoolForRemoting.submit(new Install(c, listener));
        }
    }

    private static class Install implements Callable<Void> {
        private final Computer c;
        private final TaskListener listener;

        Install(Computer c, TaskListener listener) {
            this.c = c;
            this.listener = listener;
        }

        @Override
        public Void call() throws Exception {
            install(c, listener);
            return null;
        }

        private static void install(Computer c, TaskListener listener) {
            try {
                final List<SlaveRestarter> restarters = new ArrayList<>(SlaveRestarter.all());

                VirtualChannel ch = c.getChannel();
                if (ch == null) return;  // defensive check

                List<SlaveRestarter> effective = ch.call(new FindEffectiveRestarters(restarters));

                LOGGER.log(FINE, "Effective SlaveRestarter on {0}: {1}", new Object[] {c.getName(), effective});
            } catch (Throwable e) {
                Functions.printStackTrace(e, listener.error("Failed to install restarter"));
            }
        }
    }

    private static class FindEffectiveRestarters extends MasterToSlaveCallable<List<SlaveRestarter>, IOException> {
        private final List<SlaveRestarter> restarters;

        FindEffectiveRestarters(List<SlaveRestarter> restarters) {
            this.restarters = restarters;
        }

        @Override
        public List<SlaveRestarter> call() throws IOException {
            Engine e = Engine.current();
            if (e == null) return null;    // not running under Engine

            // filter out ones that doesn't apply
            restarters.removeIf(r -> !r.canWork());

            e.addListener(new EngineListenerAdapterImpl(restarters));

            return restarters;
        }
    }

    private static final class EngineListenerAdapterImpl extends EngineListenerAdapter {
        private final List<SlaveRestarter> restarters;

        EngineListenerAdapterImpl(List<SlaveRestarter> restarters) {
            this.restarters = restarters;
        }

        @Override
        public void onReconnect() {
            try {
                for (SlaveRestarter r : restarters) {
                    try {
                        Logger.getGlobal().info("Restarting agent via " + r);
                        r.restart();
                    } catch (Exception x) {
                        Logger.getGlobal().log(SEVERE, "Failed to restart agent with " + r, x);
                    }
                }
            } finally {
                // if we move on to the reconnection without restart,
                // don't let the current implementations kick in when the agent loses connection again
                restarters.clear();
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JnlpSlaveRestarterInstaller.class.getName());

    private static final long serialVersionUID = 1L;
}
