package hudson.slaves;

import hudson.util.StreamTaskListener;
import hudson.model.Descriptor;
import hudson.model.Node;

import java.io.IOException;

/**
 * {@link ComputerLauncher} filter that can be used as a base class for decorators.
 *
 * <p>
 * Using this class also protects you from method additions in {@link ComputerLauncher},
 * since these two classes are updated in sync.
 *
 * @author Kohsuke Kawaguchi
 * @see SlaveComputer#grabLauncher(Node)
 */
public abstract class ComputerLauncherFilter extends ComputerLauncher {
    protected volatile ComputerLauncher core;

    public ComputerLauncherFilter(ComputerLauncher core) {
        this.core = core;
    }

    /**
     * Returns the delegation target.
     */
    public ComputerLauncher getCore() {
        return core;
    }

    public boolean isLaunchSupported() {
        return core.isLaunchSupported();
    }

    public void launch(SlaveComputer computer, StreamTaskListener listener) throws IOException, InterruptedException {
        core.launch(computer, listener);
    }

    public void afterDisconnect(SlaveComputer computer, StreamTaskListener listener) {
        core.afterDisconnect(computer, listener);
    }

    public void beforeDisconnect(SlaveComputer computer, StreamTaskListener listener) {
        core.beforeDisconnect(computer, listener);
    }

    public Descriptor<ComputerLauncher> getDescriptor() {
        throw new UnsupportedOperationException();
    }
}
