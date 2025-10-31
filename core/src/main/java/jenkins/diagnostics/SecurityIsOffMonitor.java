package jenkins.diagnostics;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

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
}
