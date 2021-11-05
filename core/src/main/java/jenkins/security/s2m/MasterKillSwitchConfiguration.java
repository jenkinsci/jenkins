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
     * Returns true if the configuration of this subsystem is relevant.
     *
     * <p>Historically, this was only shown when "security" (authn/authz) was enabled.
     * That missed the use case of trusted local networks and Jenkins building public (untrusted) pull requests.
     * To be sure we're not missing another case where this option is useful, just show it always.</p>
     */
    public boolean isRelevant() {
        /*
         * TODO Consider restricting this again to something like:
         * return !jenkins.clouds.isEmpty() || !jenkins.getNodes().isEmpty();
         */
        return true;
    }
}
