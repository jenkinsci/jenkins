package jenkins.tools;

import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.management.Messages;
import jenkins.model.Jenkins;

/**
 * Global configuration of tool locations and installers.
 *
 * @since 2.0
 */
@Extension
public class ToolConfigurationCategory extends GlobalConfigurationCategory {
    @Override
    public String getShortDescription() {
        return Messages.ConfigureTools_Description();
    }

    public String getDisplayName() {
        return Messages.ConfigureTools_DisplayName();
    }
}
