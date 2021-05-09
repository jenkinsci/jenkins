package jenkins.monitor;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
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
public class JavaLevelAdminMonitor extends AdministrativeMonitor {

    private static final VersionNumber RECOMMENDED_MINIMUM_VERSION = new VersionNumber("11");
    private final VersionNumber currentJavaVersion;

    @SuppressWarnings("unused")
    public JavaLevelAdminMonitor() {
        this(JavaLevelAdminMonitor.class.getName(), getJavaVersion());
    }

    public JavaLevelAdminMonitor(String id, VersionNumber currentJavaVersion) {
        super(id);
        this.currentJavaVersion = currentJavaVersion;
    }

    private static VersionNumber getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2).replace("_", ".");
        }
        return new VersionNumber(version);
    }

    @Override
    public boolean isActivated() {
        return currentJavaVersion.isOlderThan(RECOMMENDED_MINIMUM_VERSION);
    }

    @Override
    public String getDisplayName() {
        return Messages.JavaLevelAdminMonitor_DisplayName();
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
            return new HttpRedirect("https://www.jenkins.io/redirect/upgrading-jenkins-java-version");
        }
    }

}
