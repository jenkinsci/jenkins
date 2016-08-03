package jenkins.slaves;

import hudson.Extension;
import hudson.init.Terminator;
import hudson.model.Computer;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.remoting.protocol.IOHub;

/**
 * Singleton holder of {@link IOHub}
 *
 * @since FIXME
 */
@Extension
public class IOHubProvider {
    private static final Logger LOGGER = Logger.getLogger(IOHubProvider.class.getName());
    private IOHub hub;

    public IOHubProvider() {
        try {
            hub = IOHub.create(Computer.threadPoolForRemoting);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to launch IOHub", e);
            this.hub = null;
        }
    }

    public IOHub getHub() {
        return hub;
    }

    @Terminator
    public void cleanUp() throws IOException {
        if (hub != null) {
            hub.close();
            hub = null;
        }
    }


}
