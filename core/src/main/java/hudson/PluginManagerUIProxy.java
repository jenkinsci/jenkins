package hudson;

import hudson.model.Api;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.search.Search;
import hudson.search.SearchIndex;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import org.jenkinsci.bytecode.Transformer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.jar.Manifest;

/** Extension point for selective overriding parts of the PluginManager UI */
public abstract class PluginManagerUIProxy implements ExtensionPoint {
    public PluginManager getManager() {
        return Jenkins.getInstance().getPluginManager();
    }

    public static ExtensionList<PluginManagerUIProxy> all() {
        return Jenkins.getInstance().getExtensionList(PluginManagerUIProxy.class);
    }
}
