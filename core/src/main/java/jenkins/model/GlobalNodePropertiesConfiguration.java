package jenkins.model;

import hudson.Extension;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
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

    public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getGlobalNodeProperties() {
        return Jenkins.get().getGlobalNodeProperties();
    }
}
