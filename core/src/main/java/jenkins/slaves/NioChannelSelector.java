package jenkins.slaves;

import hudson.Extension;
import jenkins.util.SystemProperties;
import hudson.model.Computer;
import org.jenkinsci.remoting.nio.NioChannelHub;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton holder of {@link NioChannelHub}
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class NioChannelSelector {
    private NioChannelHub hub;

    public NioChannelSelector() {
        try {
            if (!DISABLED) {
                this.hub = new NioChannelHub(Computer.threadPoolForRemoting);
                Computer.threadPoolForRemoting.submit(hub);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to launch NIO hub",e);
            this.hub = null;
            DISABLED = true;
        }
    }

    public NioChannelHub getHub() {
        return hub;
    }

    /**
     * Escape hatch to disable use of NIO.
     */
    public static boolean DISABLED = SystemProperties.getBoolean(NioChannelSelector.class.getName()+".disabled");

    private static final Logger LOGGER = Logger.getLogger(NioChannelSelector.class.getName());
}
