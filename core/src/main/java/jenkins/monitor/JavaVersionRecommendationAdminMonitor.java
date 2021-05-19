package jenkins.monitor;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jenkins.util.java.JavaUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;

@Extension
@Restricted(NoExternalUse.class)
@Symbol("javaVersionRecommendation")
public class JavaVersionRecommendationAdminMonitor extends AdministrativeMonitor {

    @Override
    public boolean isActivated() {
        return JavaUtils.isRunningWithJava8OrBelow();
    }

    @Override
    public String getDisplayName() {
        return Messages.JavaLevelAdminMonitor_DisplayName();
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @Restricted(DoNotUse.class) // WebOnly
    @RequirePOST
    public HttpResponse doAct(@QueryParameter String no) throws IOException {
        if (no != null) { // dismiss
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            disable(true);
            return HttpResponses.redirectViaContextPath("/manage");
        } else {
            return new HttpRedirect("https://www.jenkins.io/redirect/upgrading-jenkins-java-version-8-to-11");
        }
    }

}
