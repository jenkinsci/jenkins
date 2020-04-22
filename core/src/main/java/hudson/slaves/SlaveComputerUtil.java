package hudson.slaves;

import hudson.FilePath;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;

public final class SlaveComputerUtil {
    private SlaveComputerUtil() {}
    /**
     * Obtains a {@link VirtualChannel} that allows some computation to be performed on the master.
     * This method can be called from any thread on the master, or from agent (more precisely,
     * it only works from the remoting request-handling thread in agents, which means if you've started
     * separate thread on agents, that'll fail.)
     *
     * @return null if the calling thread doesn't have any trace of where its master is.
     * @since XXX
     */
    public static VirtualChannel getChannelToMaster() {
        if (Jenkins.getInstanceOrNull()!=null) // check if calling thread is on master or on slave
            return FilePath.localChannel;

        // if this method is called from within the agent computation thread, this should work
        Channel c = Channel.current();
        if (c!=null && Boolean.TRUE.equals(c.getProperty("slave")))
            return c;

        return null;
    }
}
