package jenkins.agents;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

@Extension
@Symbol("clouds")
public class CloudsLink extends ManagementLink {

    @Override
    public String getDisplayName() {
        return Messages.CloudsLink_DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.CloudsLink_Description();
    }

    @Override
    public String getIconFileName() {
        return "symbol-cloud";
    }

    @Override
    public String getUrlName() {
        return "cloud";
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }
}
