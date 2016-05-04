package jenkins.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.DescribableList;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.List;

/**
 * Show the {@link QueueItemAuthenticator} configurations on the system config page.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 */
@Extension
public class QueueItemAuthenticatorConfiguration extends GlobalConfiguration {
    private final DescribableList<QueueItemAuthenticator,QueueItemAuthenticatorDescriptor> authenticators
        = new DescribableList<QueueItemAuthenticator, QueueItemAuthenticatorDescriptor>(this);

    public QueueItemAuthenticatorConfiguration() {
        load();
    }

    private Object readResolve() {
        authenticators.setOwner(this);
        return this;
    }

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public DescribableList<QueueItemAuthenticator, QueueItemAuthenticatorDescriptor> getAuthenticators() {
        return authenticators;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        try {
            authenticators.rebuildHetero(req,json, QueueItemAuthenticatorDescriptor.all(),"authenticators");
            return true;
        } catch (IOException e) {
            throw new FormException(e,"authenticators");
        }
    }

    public static QueueItemAuthenticatorConfiguration get() {
        return Jenkins.getInstance().getInjector().getInstance(QueueItemAuthenticatorConfiguration.class);
    }

    @Extension(ordinal = 100)
    public static class ProviderImpl extends QueueItemAuthenticatorProvider {

        @NonNull
        @Override
        public List<QueueItemAuthenticator> getAuthenticators() {
            return get().getAuthenticators();
        }
    }
}
