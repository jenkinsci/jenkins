package jenkins.model;

import hudson.Extension;
import hudson.Plugin;
import hudson.StructuredForm;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Include config.jelly defined for {@link Plugin}s.
 *
 * <p>
 * This object just acts as a proxy to configure {@link Jenkins#clouds}
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=100) @Symbol("plugin") // historically this was placed above general configuration from arbitrary descriptors
public class GlobalPluginConfiguration  extends GlobalConfiguration {
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            for( JSONObject o : StructuredForm.toList(json, "plugin"))
                Jenkins.getInstance().pluginManager.getPlugin(o.getString("name")).getPlugin().configure(req, o);
            return true;
        } catch (IOException e) {
            throw new FormException(e,"plugin");
        } catch (ServletException e) {
            throw new FormException(e,"plugin");
        }
    }
}
