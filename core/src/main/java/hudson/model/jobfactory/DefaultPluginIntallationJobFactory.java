package hudson.model.jobfactory;

import hudson.Extension;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.model.UpdateSite;
import hudson.model.UpdateSite.Plugin;
import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;

public class DefaultPluginIntallationJobFactory extends PluginIntallationJobFactory {

    @DataBoundConstructor
    public DefaultPluginIntallationJobFactory() {
    }

    @Override
    public UpdateCenterJob createPluginInstallJob(Plugin plugin, UpdateSite updateSite, Authentication authentication, boolean dynamicLoad) {
        return Jenkins.getInstance().getUpdateCenter().new InstallationJob(plugin, updateSite, authentication, dynamicLoad);
    }

    @Override
    public DefaultPluginIntallationJobFactoryDescriptor getDescriptor() {
        return (DefaultPluginIntallationJobFactoryDescriptor) Jenkins.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static class DefaultPluginIntallationJobFactoryDescriptor extends PluginIntallationJobFactoryDescriptor {

        @Override
        public String getDisplayName() {
            return "Default update center based installation";
        }

    }

}
