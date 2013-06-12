package jenkins.security;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.security.ACL;
import hudson.util.DescribableList;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Show the {@link ProjectAuthenticator} configurations on the system config page.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 */
@Extension
public class ProjectAuthenticatorConfiguration extends GlobalConfiguration {
    private final DescribableList<ProjectAuthenticator,ProjectAuthenticatorDescriptor> authenticators
        = new DescribableList<ProjectAuthenticator, ProjectAuthenticatorDescriptor>(this);

    public ProjectAuthenticatorConfiguration() {
        load();
    }

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public DescribableList<ProjectAuthenticator, ProjectAuthenticatorDescriptor> getAuthenticators() {
        return authenticators;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            authenticators.rebuildHetero(req,json, ProjectAuthenticatorDescriptor.all(),"authenticators");
            return true;
        } catch (IOException e) {
            throw new FormException(e,"authenticators");
        }
    }

    public Authentication authenticate(AbstractProject<?,?> project) {
        for (ProjectAuthenticator auth : get().getAuthenticators()) {
            Authentication a = auth.authenticate(project);
            if (a!=null)
                return a;
        }
        return ACL.SYSTEM;
    }

    public static ProjectAuthenticatorConfiguration get() {
        return Jenkins.getInstance().getInjector().getInstance(ProjectAuthenticatorConfiguration.class);
    }
}
