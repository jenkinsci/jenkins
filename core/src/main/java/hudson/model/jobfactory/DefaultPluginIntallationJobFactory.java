package hudson.model.jobfactory;

import hudson.Extension;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.model.Messages;
import hudson.model.UpdateSite;
import hudson.model.UpdateSite.Plugin;
import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Default implementation of a job factory - this is the extraction of the origin code before this extension point existed. 
 * The Installation jobs created by this factory, use the <code>url</code> of the plugin info provided by the update-center JSON to download the artifact.
 *  
 * @author Dominik Bartholdi (imod)
 */
public class DefaultPluginIntallationJobFactory extends PluginIntallationJobFactory {

    @DataBoundConstructor
    public DefaultPluginIntallationJobFactory() {
    }

    @Override
    public UpdateCenterJob createPluginInstallJob(Plugin plugin, UpdateSite updateSite, Authentication authentication, boolean dynamicLoad) {
        return new hudson.model.UpdateCenter.InstallationJob(plugin, updateSite, authentication, dynamicLoad);
    }

    @Override
    public DefaultPluginIntallationJobFactoryDescriptor getDescriptor() {
        return (DefaultPluginIntallationJobFactoryDescriptor) Jenkins.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static class DefaultPluginIntallationJobFactoryDescriptor extends PluginIntallationJobFactoryDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.DefaultPluginIntallationJobFactory_displayname();
        }

    }

}
