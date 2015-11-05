package jenkins.diagnostics;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;

/**
 * Unsecured Jenkins is, well, insecure.
 *
 * <p>
 * Call attention to the fact that Jenkins is not secured, and encourage the administrator
 * to take an action.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SecurityIsOffMonitor extends AdministrativeMonitor {
    @Override
    public boolean isActivated() {
        return !Jenkins.getInstance().isUseSecurity();
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    public void doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(req.hasParameter("no")) {
            disable(true);
            rsp.sendRedirect(req.getContextPath()+"/manage");
        } else {
            rsp.sendRedirect(req.getContextPath()+"/configureSecurity");
        }
    }
}
