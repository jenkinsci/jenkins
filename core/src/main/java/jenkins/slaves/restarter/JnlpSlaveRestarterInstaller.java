package jenkins.slaves.restarter;

import hudson.Extension;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Engine;
import hudson.remoting.EngineListener;
import hudson.remoting.EngineListenerAdapter;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import jenkins.model.Jenkins.MasterComputer;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import jenkins.security.MasterToSlaveCallable;

/**
 * Actual agent restart logic.
 *
 * <p>
 * Use {@link ComputerListener} to install {@link EngineListener}, which in turn gets executed when
 * the agent gets disconnected.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class JnlpSlaveRestarterInstaller extends ComputerListener implements Serializable {
    @Override
    public void onOnline(final Computer c, final TaskListener listener) throws IOException, InterruptedException {
        MasterComputer.threadPoolForRemoting.submit(new Install(c, listener));
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
    }

    private static void install(Computer c, TaskListener listener) {
        try {
            final List<SlaveRestarter> restarters = new ArrayList<>(SlaveRestarter.all());

            VirtualChannel ch = c.getChannel();
            if (ch==null) return;  // defensive check

            List<SlaveRestarter> effective = ch.call(new FindEffectiveRestarters(restarters));

            LOGGER.log(FINE, "Effective SlaveRestarter on {0}: {1}", new Object[] {c.getName(), effective});
        } catch (Throwable e) {
            Functions.printStackTrace(e, listener.error("Failed to install restarter"));
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

            try {
                Engine.class.getMethod("addListener", EngineListener.class);
            } catch (NoSuchMethodException ignored) {
                return null;    // running with older version of remoting that doesn't support adding listener
            }

            // filter out ones that doesn't apply
            restarters.removeIf(r -> !r.canWork());

            e.addListener(new EngineListenerAdapter() {
                @Override
                public void onReconnect() {
                    try {
                        for (SlaveRestarter r : restarters) {
                            try {
                                LOGGER.info("Restarting agent via "+r);
                                r.restart();
                            } catch (Exception x) {
                                LOGGER.log(SEVERE, "Failed to restart agent with "+r, x);
                            }
                        }
                    } finally {
                        // if we move on to the reconnection without restart,
                        // don't let the current implementations kick in when the agent loses connection again
                        restarters.clear();
                    }
                }
            });

            return restarters;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JnlpSlaveRestarterInstaller.class.getName());

    private static final long serialVersionUID = 1L;
}
