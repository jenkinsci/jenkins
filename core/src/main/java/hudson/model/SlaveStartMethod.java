package hudson.model;

import hudson.ExtensionPoint;
import hudson.model.Slave.ComputerImpl;
import hudson.remoting.Channel.Listener;
import hudson.util.DescriptorList;
import hudson.util.StreamTaskListener;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Extension point to allow control over how Slaves are started.
 *
 * @author Stephen Connolly
 * @since 24-Apr-2008 22:12:35
 */
public abstract class SlaveStartMethod implements Describable<SlaveStartMethod>, ExtensionPoint {
    /**
     * Returns true if this {@link SlaveStartMethod} supports
     * programatic launch of the slave agent in the target {@link Computer}.
     */
    public boolean isLaunchSupported() {
        return true;
    }

    /**
     * Launches the slave agent for the given {@link Computer}.
     *
     * <p>
     * If the slave agent is launched successfully, {@link ComputerImpl#setChannel(InputStream, OutputStream, OutputStream, Listener)}
     * should be invoked in the end to notify Hudson of the established connection.
     * The operation could also fail, in which case there's no need to make any callback notification,
     * (except to notify the user of the failure through {@link StreamTaskListener}.)
     *
     * @param listener
     *      The progress of the launch, as well as any error, should be sent to this listener.
     */
    public abstract void launch(Slave.ComputerImpl computer, StreamTaskListener listener);

    /**
     * All registered {@link SlaveStartMethod} implementations.
     */
    public static final DescriptorList<SlaveStartMethod> LIST = new DescriptorList<SlaveStartMethod>();
}
