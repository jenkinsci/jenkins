package jenkins.security.s2m;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import javax.inject.Inject;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

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
        if (!jenkins.hasPermission(Jenkins.RUN_SCRIPTS))) {
            return false;
        }
        if (rule.getMasterKillSwitch()) {
            return true; // always relevant if it is enabled.
        }
        return jenkins.isUseSecurity()            // if security is off, there's no point
            && (jenkins.getComputers().length>1   // if there's no slave,
                || !jenkins.clouds.isEmpty()      // and no clouds, likewise this is pointless
                || Relevance.fromExtension()      // unless a plugin thinks otherwise
            )
        ;
    }

    /**
     * Some plugins may cause the {@link MasterKillSwitchConfiguration} to be relevant for additional reasons,
     * by implementing this extension point they can indicate such additional conditions.
     *
     * @since FIXME
     */
    public static abstract class Relevance implements ExtensionPoint {

        /**
         * Is the {@link MasterKillSwitchConfiguration} relevant.
         *
         * @return {@code true} if the {@link MasterKillSwitchConfiguration} relevant.
         */
        public abstract boolean isRelevant();

        /**
         * Is the {@link MasterKillSwitchConfiguration} relevant for any of the {@link Relevance} extensions.
         *
         * @return {@code true} if and only if {@link Relevance#isRelevant()} for at least one extension.
         */
        public static boolean fromExtension() {
            for (Relevance r : ExtensionList.lookup(Relevance.class)) {
                if (r.isRelevant()) {
                    return true;
                }
            }
            return false;
        }
    }
}

