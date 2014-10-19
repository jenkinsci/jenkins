package jenkins.security.admin;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import jenkins.model.Jenkins;
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
    Jenkins jenkins;

    @Override
    public boolean isActivated() {
        return rule.getMasterKillSwitch()
            && jenkins.getComputers().length>1  // if there's no slave, there's no point
            && jenkins.isUseSecurity()          // if security is off, likewise this is pointless
            ;
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
