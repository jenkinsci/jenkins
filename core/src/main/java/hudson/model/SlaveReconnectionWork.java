package hudson.model;

import hudson.model.Slave.ComputerImpl;
import hudson.triggers.SafeTimerTask;

/**
 * Periodically checks the slaves and try to reconnect dead slaves.
 *
 * @author Kohsuke Kawaguchi
 */
public class SlaveReconnectionWork extends SafeTimerTask {
    protected void doRun() {
        for(Slave s : Hudson.getInstance().getSlaves()) {
            ComputerImpl c = s.getComputer();
            if(c==null) // shouldn't happen, but let's be defensive
                continue;
            if(c.isOffline() && c.isStartSupported())
                c.tryReconnect();
        }
    }
}
