package jenkins.diagnostics;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Unsecured Jenkins is, well, insecure.
 *
 * <p>
 * Call attention to the fact that Jenkins is not secured, and encourage the administrator
 * to take an action.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("securityIsOff")
public class SecurityIsOffMonitor extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return Messages.SecurityIsOffMonitor_DisplayName();
    }

    @Override
    public boolean isActivated() {
        return !Jenkins.get().isUseSecurity();
    }

    @Override
    public boolean isSecurity() {
        return true;
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @RequirePOST
    public void doAct(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (req.hasParameter("no")) {
            disable(true);
            rsp.sendRedirect(req.getContextPath() + "/manage");
        } else {
            rsp.sendRedirect(req.getContextPath() + "/configureSecurity");
        }
    }
}
