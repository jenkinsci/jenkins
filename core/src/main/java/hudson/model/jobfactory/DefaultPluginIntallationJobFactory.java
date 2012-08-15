package hudson.model.jobfactory;

import hudson.Extension;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.model.UpdateSite;
import hudson.model.UpdateSite.Plugin;
import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;

public class DefaultPluginIntallationJobFactory extends PluginIntallationJobFactory {

    private String name;

    // FIXME remove name parameter
    @DataBoundConstructor
    public DefaultPluginIntallationJobFactory(String name) {
        System.out.println(">>name: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
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
            return "Default update center based installation";
        }

    }

    @Override
    public String toString() {
        return this.getClass().getName() + ": " + name;
    }

}
