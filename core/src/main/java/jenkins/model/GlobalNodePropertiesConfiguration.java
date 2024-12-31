package jenkins.model;

import hudson.Extension;
import hudson.agents.NodeProperty;
import hudson.agents.NodePropertyDescriptor;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Include {@link NodePropertyDescriptor} configurations.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = 110) @Symbol("nodeProperties") // historically this was placed above GlobalPluginConfiguration
public class GlobalNodePropertiesConfiguration extends GlobalConfiguration {
    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        try {
            Jenkins j = Jenkins.get();
            JSONObject np = json.getJSONObject("globalNodeProperties");
            if (!np.isNullObject()) {
                j.getGlobalNodeProperties().rebuild(req, np, NodeProperty.for_(j));
            }
            return true;
        } catch (IOException e) {
            throw new FormException(e, "globalNodeProperties");
        }
    }
}
