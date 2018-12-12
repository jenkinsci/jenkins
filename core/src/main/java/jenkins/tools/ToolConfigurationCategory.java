package jenkins.tools;

import hudson.Extension;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.management.Messages;

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
