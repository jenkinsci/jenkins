package jenkins.security.s2m;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;

import javax.inject.Inject;
import java.io.IOException;

/**
 * If {@link AdminWhitelistRule#masterKillSwitch} is on, warn the user.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
@Extension
public class MasterKillSwitchWarning extends AdministrativeMonitor {
    @Inject
    AdminWhitelistRule rule;

    @Inject
    MasterKillSwitchConfiguration config;

    @Override
    public boolean isActivated() {
        return rule.getMasterKillSwitch() && config.isRelevant();
    }

    @Override
    public String getDisplayName() {
        return Messages.MasterKillSwitchWarning_DisplayName();
    }

    public HttpResponse doAct(@QueryParameter String dismiss) throws IOException {
        if(dismiss!=null) {
            disable(true);
            return HttpResponses.redirectViaContextPath("/manage");
        } else {
            return HttpResponses.redirectViaContextPath("configureSecurity");
        }
    }
}
