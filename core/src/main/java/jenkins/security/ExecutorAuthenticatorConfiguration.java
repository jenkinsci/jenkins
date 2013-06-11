package jenkins.security;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.util.DescribableList;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Show the {@link ExecutorAuthenticator} configurations on the system config page.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ExecutorAuthenticatorConfiguration extends GlobalConfiguration {
    private final DescribableList<ExecutorAuthenticator,ExecutorAuthenticatorDescriptor> authenticators
        = new DescribableList<ExecutorAuthenticator, ExecutorAuthenticatorDescriptor>(this);

    public ExecutorAuthenticatorConfiguration() {
        load();
    }

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public DescribableList<ExecutorAuthenticator, ExecutorAuthenticatorDescriptor> getAuthenticators() {
        return authenticators;
    }

    // TODO: get rid of this method by making PersistedList derive from List via the use of bridge-method injector
    @Restricted(NoExternalUse.class)
    public DescriptorExtensionList<ExecutorAuthenticator,ExecutorAuthenticatorDescriptor> getAuthenticatorsDescriptors() {
        return ExecutorAuthenticatorDescriptor.all();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            authenticators.rebuildHetero(req,json,ExecutorAuthenticatorDescriptor.all(),"authenticators");
            return true;
        } catch (IOException e) {
            throw new FormException(e,"authenticators");
        }
    }
}
