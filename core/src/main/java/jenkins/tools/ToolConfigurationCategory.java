package jenkins.tools;

import hudson.Extension;
import jenkins.management.Messages;
import jenkins.model.GlobalConfigurationCategory;

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

    @Override
    public String getDisplayName() {
        return Messages.ConfigureTools_DisplayName();
    }
}
