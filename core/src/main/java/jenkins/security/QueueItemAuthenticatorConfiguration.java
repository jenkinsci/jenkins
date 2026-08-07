package jenkins.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.PersistentDescriptor;
import hudson.model.queue.Tasks;
import hudson.util.DescribableList;
import java.io.IOException;
import java.util.List;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Show the {@link QueueItemAuthenticator} configurations on the system config page.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 */
@Extension @Symbol("queueItemAuthenticator")
public class QueueItemAuthenticatorConfiguration extends GlobalConfiguration implements PersistentDescriptor {
    private final DescribableList<QueueItemAuthenticator, QueueItemAuthenticatorDescriptor> authenticators
        = new DescribableList<>(this);

    private Object readResolve() {
        authenticators.setOwner(this);
        return this;
    }

    @Override
    public @NonNull GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    /**
     * Provides all user-configured authenticators.
     * Note that if you are looking to determine all <em>effective</em> authenticators,
     * including any potentially supplied by plugins rather than user configuration,
     * you should rather call {@link QueueItemAuthenticatorProvider#authenticators};
     * or if you are looking for the authentication of an actual project, build, etc., use
     * {@link hudson.model.Queue.Item#authenticate} or {@link Tasks#getAuthenticationOf}.
     */
    public DescribableList<QueueItemAuthenticator, QueueItemAuthenticatorDescriptor> getAuthenticators() {
        return authenticators;
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        try {
            authenticators.rebuildHetero(req, json, QueueItemAuthenticatorDescriptor.all(), "authenticators");
            return true;
        } catch (IOException e) {
            throw new FormException(e, "authenticators");
        }
    }

    public static @NonNull QueueItemAuthenticatorConfiguration get() {
        return GlobalConfiguration.all().getInstance(QueueItemAuthenticatorConfiguration.class);
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
