package jenkins.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import java.io.IOException;
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
public class GlobalComputerRetentionCheckIntervalConfiguration extends GlobalConfiguration {
    public int getComputerRetentionCheckInterval() {
        return Jenkins.get().getComputerRetentionCheckInterval();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            final int interval = json.getInt("computerRetentionCheckInterval");
            if (interval <= 0) {
                throw new FormException("must be greater than zero", "computerRetentionCheckInterval");
            }
            Jenkins.get().setComputerRetentionCheckInterval(interval);
            return true;
        } catch (IOException e) {
            throw new FormException(e, "computerRetentionCheckInterval");
        } catch (JSONException e) {
            throw new FormException(e.getMessage(), "computerRetentionCheckInterval");
        }
    }

    @NonNull
    @Override
    public Permission getRequiredGlobalConfigPagePermission() {
        return Jenkins.ADMINISTER;
    }
}
