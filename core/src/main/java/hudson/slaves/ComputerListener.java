package hudson.slaves;

import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.ExtensionPoint;

/**
 * Receives notifications about status changes of {@link Computer}s.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.246
 */
public abstract class ComputerListener implements ExtensionPoint {
    /**
     * Called right after a {@link Computer} comes online.
     */
    public void onOnline(Computer c) {}

    /**
     * Called right after a {@link Computer} went offline.
     */
    public void onOffline(Computer c) {}

    /**
     * Registers this {@link ComputerListener} so that it will start receiving events.
     */
    public final void register() {
        Hudson.getInstance().getComputerListeners().add(this);
    }

    /**
     * Unregisters this {@link ComputerListener} so that it will never receive further events.
     *
     * <p>
     * Unless {@link ComputerListener} is unregistered, it will never be a subject of GC.
     */
    public final boolean unregister() {
        return Hudson.getInstance().getComputerListeners().remove(this);
    }
}
