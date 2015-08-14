package hudson;

import hudson.model.Api;
import hudson.model.Descriptor;
import hudson.search.Search;
import hudson.search.SearchIndex;
import net.sf.json.JSONArray;
import org.jenkinsci.bytecode.Transformer;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.jar.Manifest;

/** Default UI proxy implementation that delegates method calls to the pluginManager */
public abstract class AbtractPluginManagerUIProxy implements PluginManagerUIProxy {
    protected PluginManager manager;

    public PluginManager getManager() {
        return manager;
    }

    public void setManager(PluginManager manager) {
        this.manager = manager;
    }

    public AbtractPluginManagerUIProxy() {

    }

    public AbtractPluginManagerUIProxy(PluginManager manager) {
        this.manager = manager;
    }

    // Methods from PluginManager that are exposed for Stapler

    @Override
    public Transformer getCompatibilityTransformer() {
        return manager.getCompatibilityTransformer();
    }

    @Override
    public Api getApi() {
        return manager.getApi();
    }

    @Override
    public Manifest getBundledPluginManifest(String shortName) {
        return manager.getBundledPluginManifest(shortName);
    }

    @Override
    public PluginStrategy getPluginStrategy() {
        return manager.getPluginStrategy();
    }

    @Override
    public List<PluginWrapper> getPlugins() {
        return manager.getPlugins();
    }

    @Override
    public List<PluginManager.FailedPlugin> getFailedPlugins() {
        return manager.getFailedPlugins();
    }

    @Override
    public PluginWrapper getPlugin(String shortName) {
        return manager.getPlugin(shortName);
    }

    @Override
    public PluginWrapper getPlugin(Class<? extends Plugin> pluginClazz) {
        return manager.getPlugin(pluginClazz);
    }

    @Override
    public List<PluginWrapper> getPlugins(Class<? extends Plugin> pluginSuperclass) {
        return manager.getPlugins(pluginSuperclass);
    }

    @Override
    public String getDisplayName() {
        return manager.getDisplayName();
    }

    @Override
    public String getSearchUrl() {
        return manager.getSearchUrl();
    }

    @Override
    public HttpResponse doUpdateSources(StaplerRequest req) throws IOException {
        return manager.doUpdateSources(req);
    }

    @Override
    public void doInstall(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        manager.doInstall(req, rsp);
    }

    @Override
    public HttpResponse doSiteConfigure(@QueryParameter String site) throws IOException {
        return manager.doSiteConfigure(site);
    }

    @Override
    public HttpResponse doProxyConfigure(StaplerRequest req) throws IOException, ServletException {
        return manager.doProxyConfigure(req);
    }

    @Override
    public HttpResponse doUploadPlugin(StaplerRequest req) throws IOException, ServletException {
        return manager.doUploadPlugin(req);
    }

    @Override
    public HttpResponse doCheckUpdatesServer() throws IOException {
        return manager.doCheckUpdatesServer();
    }

    @Override
    public Descriptor<ProxyConfiguration> getProxyDescriptor() {
        return manager.getProxyDescriptor();
    }

    @Override
    public JSONArray doPrevalidateConfig(StaplerRequest req) throws IOException {
        return manager.doPrevalidateConfig(req);
    }

    @Override
    public HttpResponse doInstallNecessaryPlugins(StaplerRequest req) throws IOException {
        return manager.doInstallNecessaryPlugins(req);
    }

    public SearchIndex getSearchIndex() {
        return manager.getSearchIndex();
    }

    public Search getSearch() {
        return manager.getSearch();
    }

    public String getSearchName() {
        return manager.getSearchName();
    }
}
