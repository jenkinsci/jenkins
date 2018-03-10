package jenkins.model;

import hudson.Extension;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Include {@link NodePropertyDescriptor} configurations.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=110) @Symbol("nodeProperties") // historically this was placed above GlobalPluginConfiguration
public class GlobalNodePropertiesConfiguration extends GlobalConfiguration {
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            Jenkins j = Jenkins.getInstance();
            JSONObject np = json.getJSONObject("globalNodeProperties");
            if (!np.isNullObject()) {
                j.getGlobalNodeProperties().rebuild(req, np, NodeProperty.for_(j));
            }
            return true;
        } catch (IOException e) {
            throw new FormException(e,"globalNodeProperties");
        }
    }
}
