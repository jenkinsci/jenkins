package jenkins.tools;

import hudson.Extension;
import jenkins.model.GlobalConfigurationCategory;

/**
 * Global configuration of tool locations and installers.
 *
 * @since TODO
 */
@Extension
public class ToolConfigurationCategory extends GlobalConfigurationCategory {
    @Override
    public String getShortDescription() {
        return jenkins.management.Messages.ConfigureTools_Description();
    }

    public String getDisplayName() {
        return jenkins.management.Messages.ConfigureTools_DisplayName();
    }
}
