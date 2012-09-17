package jenkins.model;

import hudson.Extension;
import hudson.slaves.Cloud;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Adds the {@link Cloud} configuration to the system config page.
 *
 * <p>
 * This object just acts as a proxy to configure {@link Jenkins#clouds}
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=-100) // historically this was placed at the very end of the configuration page
public class GlobalCloudConfiguration  extends GlobalConfiguration {
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            Jenkins.getInstance().clouds.rebuildHetero(req,json, Cloud.all(), "cloud");
            return true;
        } catch (IOException e) {
            throw new FormException(e,"clouds");
        }
    }
}
