package jenkins.security;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Api;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * An extension point for authorizing REST API access to an object where an unsafe result type would be produced.
 * Both JSONP and XPath with primitive result sets are considered unsafe due to CSRF attacks.
 * A default implementation allows requests if a deprecated system property is set, or if Jenkins is unsecured anyway,
 * but plugins may offer implementations which authorize scripted clients, requests from inside a trusted domain, etc.
 * @see Api
 * @since 1.537
 */
public interface SecureRequester extends ExtensionPoint {

    /**
     * Checks if a Jenkins object can be accessed by a given REST request.
     * For instance, if the {@link StaplerRequest2#getReferer} matches a given host, or
     * anonymous read is allowed for the given object.
     * @param req a request going through the REST API
     * @param bean an exported object of some kind
     * @return true if this requester should be trusted, false to reject
     */
    boolean permit(StaplerRequest2 req, Object bean);

    @Restricted(NoExternalUse.class)
    @Extension class Default implements SecureRequester {

        private static final String PROP = "hudson.model.Api.INSECURE";
        private static final boolean INSECURE = SystemProperties.getBoolean(PROP);

        static {
            if (INSECURE) {
                Logger.getLogger(SecureRequester.class.getName()).warning(PROP + " system property is deprecated; implement SecureRequester instead");
            }
        }

        @Override public boolean permit(StaplerRequest2 req, Object bean) {
            return INSECURE || !Jenkins.get().isUseSecurity();
        }

    }

}
