package hudson.model.jobfactory;

import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.model.UpdateSite;
import hudson.model.UpdateSite.Plugin;
import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;

/**
 * Defines the factory to create the plugin installation jobs. The idea behind this is to allow different implementations on how a plugin is downloaded.
 * e.g.
 * <li>default implementation uses a simple HTTP GET Request on a final URL
 * <li>resolving of the plugin in a maven repository
 * @see DefaultPluginIntallationJobFactory
 */
public abstract class PluginIntallationJobFactory implements Describable<PluginIntallationJobFactory> {

    public static ExtensionList<PluginIntallationJobFactory> all() {
        return Jenkins.getInstance().getExtensionList(PluginIntallationJobFactory.class);
    }

    /**
     * Creates a job able to download and install the plugin.
     * 
     * @param plugin
     *            the plugin to be implemented
     * @param updateSite
     *            the update site having all meta information
     * @param authentication
     *            the authentication of the current active user
     * @param dynamicLoad
     *            whether to do a dynamic installation of the plugin (installation wihout restart)
     * @return a job to be executed
     */
    public abstract UpdateCenterJob createPluginInstallJob(Plugin plugin, UpdateSite updateSite, Authentication authentication, boolean dynamicLoad);

    @Override
    public PluginIntallationJobFactoryDescriptor getDescriptor() {
        return (PluginIntallationJobFactoryDescriptor) Jenkins.getInstance().getDescriptor(getClass());
    }

    public static abstract class PluginIntallationJobFactoryDescriptor extends Descriptor<PluginIntallationJobFactory> {
        protected PluginIntallationJobFactoryDescriptor() {
        }
    }
}
