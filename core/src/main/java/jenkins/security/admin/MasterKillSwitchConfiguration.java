package jenkins.security.admin;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import javax.inject.Inject;

/**
 * Exposes {@link AdminWhitelistRule#masterKillSwitch} to the admin.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
@Extension
public class MasterKillSwitchConfiguration extends GlobalConfiguration {
    @Inject
    AdminWhitelistRule rule;

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public boolean getMasterToSlaveAccessControl() {
        return !rule.getMasterKillSwitch();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        rule.setMasterKillSwitch(!json.has("masterToSlaveAccessControl"));
        return true;
    }
}

