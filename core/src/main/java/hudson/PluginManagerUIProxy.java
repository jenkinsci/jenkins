package hudson;

import hudson.model.Api;
import hudson.model.Descriptor;
import hudson.search.Search;
import hudson.search.SearchIndex;
import net.sf.json.JSONArray;
import org.jenkinsci.bytecode.Transformer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.jar.Manifest;

/**
 * This allows an object to be declared that overrides the PluginManager UI completely
 * It must delegate method calls to the PluginManager
 */
public interface PluginManagerUIProxy {
    Transformer getCompatibilityTransformer();

    Api getApi();

    Manifest getBundledPluginManifest(String shortName);

    PluginStrategy getPluginStrategy();

    @Exported
    List<PluginWrapper> getPlugins();

    List<PluginManager.FailedPlugin> getFailedPlugins();

    PluginWrapper getPlugin(String shortName);

    PluginWrapper getPlugin(Class<? extends Plugin> pluginClazz);

    List<PluginWrapper> getPlugins(Class<? extends Plugin> pluginSuperclass);

    String getDisplayName();

    String getSearchUrl();

    HttpResponse doUpdateSources(StaplerRequest req) throws IOException;

    void doInstall(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException;

    HttpResponse doSiteConfigure(@QueryParameter String site) throws IOException;

    HttpResponse doProxyConfigure(StaplerRequest req) throws IOException, ServletException;

    HttpResponse doUploadPlugin(StaplerRequest req) throws IOException, ServletException;

    @Restricted(NoExternalUse.class)
    HttpResponse doCheckUpdatesServer() throws IOException;

    Descriptor<ProxyConfiguration> getProxyDescriptor();

    JSONArray doPrevalidateConfig(StaplerRequest req) throws IOException;

    HttpResponse doInstallNecessaryPlugins(StaplerRequest req) throws IOException;

    // Methods from AbstractModelObject

    SearchIndex getSearchIndex();
    Search getSearch();
    String getSearchName();


    // HAS NOT HAD FIELDS MAPPED OUT, so far I'm still verifying if they have *any* staplerAccessors
}
