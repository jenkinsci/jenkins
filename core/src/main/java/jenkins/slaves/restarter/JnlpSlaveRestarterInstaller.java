package jenkins.slaves.restarter;

import hudson.Extension;
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
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import jenkins.security.MasterToSlaveCallable;

/**
 * Actual slave restart logic.
 *
 * <p>
 * Use {@link ComputerListener} to install {@link EngineListener}, which in turn gets executed when
 * the slave gets disconnected.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class JnlpSlaveRestarterInstaller extends ComputerListener implements Serializable {
    @Override
    public void onOnline(final Computer c, final TaskListener listener) throws IOException, InterruptedException {
        MasterComputer.threadPoolForRemoting.submit(new java.util.concurrent.Callable<Void>() {
            @Override
            public Void call() throws Exception {
                install(c, listener);
                return null;
            }
        });
    }

    private void install(Computer c, TaskListener listener) {
        try {
            final List<SlaveRestarter> restarters = new ArrayList<SlaveRestarter>(SlaveRestarter.all());

            VirtualChannel ch = c.getChannel();
            if (ch==null) return;  // defensive check

            List<SlaveRestarter> effective = ch.call(new MasterToSlaveCallable<List<SlaveRestarter>, IOException>() {
                public List<SlaveRestarter> call() throws IOException {
                    Engine e = Engine.current();
                    if (e == null) return null;    // not running under Engine

                    try {
                        Engine.class.getMethod("addListener", EngineListener.class);
                    } catch (NoSuchMethodException _) {
                        return null;    // running with older version of remoting that doesn't support adding listener
                    }

                    // filter out ones that doesn't apply
                    for (Iterator<SlaveRestarter> itr = restarters.iterator(); itr.hasNext(); ) {
                        SlaveRestarter r =  itr.next();
                        if (!r.canWork())
                            itr.remove();
                    }

                    e.addListener(new EngineListenerAdapter() {
                        @Override
                        public void onReconnect() {
                            try {
                                for (SlaveRestarter r : restarters) {
                                    try {
                                        LOGGER.info("Restarting slave via "+r);
                                        r.restart();
                                    } catch (Exception x) {
                                        LOGGER.log(SEVERE, "Failed to restart slave with "+r, x);
                                    }
                                }
                            } finally {
                                // if we move on to the reconnection without restart,
                                // don't let the current implementations kick in when the slave loses connection again
                                restarters.clear();
                            }
                        }
                    });

                    return restarters;
                }
            });

            LOGGER.log(FINE, "Effective SlaveRestarter on {0}: {1}", new Object[] {c.getName(), effective});
        } catch (Throwable e) {
            e.printStackTrace(listener.error("Failed to install restarter"));
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JnlpSlaveRestarterInstaller.class.getName());

    private static final long serialVersionUID = 1L;
}
