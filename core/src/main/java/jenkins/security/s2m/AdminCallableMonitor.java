package jenkins.security.s2m;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AdministrativeMonitor;
import hudson.remoting.Callable;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Report any rejected {@link Callable}s and {@link FilePath} executions and allow
 * admins to whitelist them.
 *
 * @since 1.THU
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("slaveToMasterAccessControl")
public class AdminCallableMonitor extends AdministrativeMonitor {
    @Inject
    Jenkins jenkins;

    @Inject
    AdminWhitelistRule rule;

    public AdminCallableMonitor() {
        super("slaveToMasterAccessControl");
    }

    @Override
    public boolean isActivated() {
        return !rule.rejected.describe().isEmpty();
    }

    @Override
    public String getDisplayName() {
        return Messages.AdminCallableMonitor_DisplayName();
    }

    // bind this to URL
    public AdminWhitelistRule getRule() {
        return rule;
    }

    /**
     * Depending on whether the user said "examin" or "dismiss", send him to the right place.
     */
    public HttpResponse doAct(@QueryParameter String dismiss) throws IOException {
        if(dismiss!=null) {
            disable(true);
            return HttpResponses.redirectViaContextPath("/manage");
        } else {
            return HttpResponses.redirectTo("rule/");
        }
    }

    public HttpResponse doIndex() {
        return HttpResponses.redirectTo("rule/");
    }
}
