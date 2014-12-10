package jenkins.security.s2m;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
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

    @Inject
    Jenkins jenkins;

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public boolean getMasterToSlaveAccessControl() {
        return !rule.getMasterKillSwitch();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        if (isRelevant()) {
            // don't record on/off unless this becomes relevant, so that we can differentiate
            // those who have disabled vs those who haven't cared.
            rule.setMasterKillSwitch(!json.has("masterToSlaveAccessControl"));
        }
        return true;
    }

    /**
     * Returns true if the configuration of this subsystem becomes relevant.
     * Unless this option is relevant, we don't let users choose this.
     */
    public boolean isRelevant() {
        return jenkins.getComputers().length>1  // if there's no slave, there's no point
            && jenkins.isUseSecurity()          // if security is off, likewise this is pointless
        ;
    }
}

