package jenkins.model;

import hudson.Extension;
import hudson.model.PersistentDescriptor;
import java.util.logging.Logger;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Configures check interval for computer retention.
 *
 * @author Jakob Ackermann
 */
@Extension(ordinal = 401) @Symbol("computerRetentionCheckInterval")
public class GlobalComputerRetentionCheckIntervalConfiguration extends GlobalConfiguration implements PersistentDescriptor {
    /**
     * For {@link hudson.slaves.ComputerRetentionWork#getRecurrencePeriod()}
     */
    private int computerRetentionCheckInterval = 60;

    /**
     * Gets the check interval for computer retention.
     *
     * @since 2.463
     */
    public int getComputerRetentionCheckInterval() {
        if (computerRetentionCheckInterval <= 0) {
            LOGGER.info("computerRetentionCheckInterval must be greater than zero, falling back to 60s");
            return 60;
        }
        if (computerRetentionCheckInterval > 60) {
            LOGGER.info("computerRetentionCheckInterval is limited to 60s");
            return 60;
        }
        return computerRetentionCheckInterval;
    }

    /**
     * Updates the check interval for computer retention and restarts the check cycle.
     *
     * @param interval new check interval in seconds
     * @throws IllegalArgumentException interval must be greater than zero
     * @since 2.463
     */
    private void setComputerRetentionCheckInterval(int interval) throws IllegalArgumentException {
        if (interval <= 0) {
            throw new IllegalArgumentException("interval must be greater than zero");
        }
        if (interval > 60) {
            throw new IllegalArgumentException("interval must be below or equal 60s");
        }
        computerRetentionCheckInterval = interval;
        save();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            final int interval = json.getInt("computerRetentionCheckInterval");
            setComputerRetentionCheckInterval(interval);
            return true;
        } catch (IllegalArgumentException e) {
            throw new FormException(e, "computerRetentionCheckInterval");
        } catch (JSONException e) {
            throw new FormException(e.getMessage(), "computerRetentionCheckInterval");
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GlobalComputerRetentionCheckIntervalConfiguration.class.getName());
}
