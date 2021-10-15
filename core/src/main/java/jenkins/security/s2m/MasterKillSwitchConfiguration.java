package jenkins.security.s2m;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import javax.inject.Inject;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Exposes {@code AdminWhitelistRule#masterKillSwitch} to the admin.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.587 / 1.580.1
 */
@Extension
public class MasterKillSwitchConfiguration extends GlobalConfiguration {
    @Inject
    AdminWhitelistRule rule;

    @Inject
    Jenkins jenkins;

    @Override
    public @NonNull GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    /**
     * @deprecated Use {@link #getAgentToControllerAccessControl()} instead
     */
    @Deprecated
    public boolean getMasterToSlaveAccessControl() {
        return getAgentToControllerAccessControl();
    }

    /**
     * @since TODO
     */
    public boolean getAgentToControllerAccessControl() {
        return !rule.getMasterKillSwitch();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        if (isRelevant()) {
            // don't record on/off unless this becomes relevant, so that we can differentiate
            // those who have disabled vs those who haven't cared.
            rule.setMasterKillSwitch(!json.has("agentToControllerAccessControl"));
        }
        return true;
    }

    /**
     * Returns true if the configuration of this subsystem becomes relevant.
     * Unless this option is relevant, we don't let users choose this.
     */
    public boolean isRelevant() {
        return jenkins.hasPermission(Jenkins.ADMINISTER) && jenkins.isUseSecurity();
    }
}
