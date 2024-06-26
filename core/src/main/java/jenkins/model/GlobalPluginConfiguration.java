package jenkins.model;

import hudson.Extension;
import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.StructuredForm;
import jakarta.servlet.ServletException;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Include config.jelly defined for {@link Plugin}s.
 *
 * <p>
 * This object just acts as a proxy to configure {@link Jenkins#clouds}
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = 100) @Symbol("plugin") // historically this was placed above general configuration from arbitrary descriptors
public class GlobalPluginConfiguration  extends GlobalConfiguration {
    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        try {
            for (JSONObject o : StructuredForm.toList(json, "plugin")) {
                String pluginName = o.getString("name");
                PluginWrapper pw = Jenkins.get().pluginManager.getPlugin(pluginName);
                Plugin p = pw != null ? pw.getPlugin() : null;
                if (p == null) {
                    throw new FormException("Cannot find the plugin instance: " + pluginName, "plugin");
                }
                p.configure(req, o);
            }
            return true;
        } catch (IOException | ServletException e) {
            throw new FormException(e, "plugin");
        }
    }
}
