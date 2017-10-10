package jenkins.security.csrf;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.security.csrf.CrumbIssuer;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Monitor that the CSRF is enabled on the application.
 *
 * @author Wadeck Follonier
 * @author Oleg Nenashev
 * @since TODO
 */
@Extension
@Symbol("csrf")
@Restricted(NoExternalUse.class)
public class CSRFAdministrativeMonitor extends AdministrativeMonitor {
    @Override
    public String getDisplayName() {
        return Messages.CSRFAdministrativeMonitor_displayName();
    }
    
    @Override
    public boolean isActivated() {
        Jenkins j = Jenkins.getInstance();
        CrumbIssuer currentIssuer = j.getCrumbIssuer();
        if (currentIssuer == null) {
            return true;
        }
        
        return false;
    }
}
