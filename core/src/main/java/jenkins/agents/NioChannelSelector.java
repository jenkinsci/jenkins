package jenkins.agents;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.init.Terminator;
import hudson.model.Computer;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.jenkinsci.remoting.nio.NioChannelHub;

/**
 * Singleton holder of {@link NioChannelHub}
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class NioChannelSelector {
    private NioChannelHub hub;

    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "field is static for script console")
    public NioChannelSelector() {
        try {
            if (!DISABLED) {
                this.hub = new NioChannelHub(Computer.threadPoolForRemoting);
                Computer.threadPoolForRemoting.submit(hub);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to launch NIO hub", e);
            this.hub = null;
            DISABLED = true;
        }
    }

    public NioChannelHub getHub() {
        return hub;
    }

    @Terminator
    public void cleanUp() throws IOException {
        if (hub != null) {
            hub.close();
            hub = null;
        }
    }

    /**
     * Escape hatch to disable use of NIO.
     */
    static boolean DISABLED = SystemProperties.getBoolean(NioChannelSelector.class.getName() + ".disabled");

    private static final Logger LOGGER = Logger.getLogger(NioChannelSelector.class.getName());
}
