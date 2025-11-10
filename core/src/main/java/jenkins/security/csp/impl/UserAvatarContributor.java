package jenkins.security.csp.impl;

import hudson.Extension;
import hudson.model.User;
import hudson.tasks.UserAvatarResolver;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.navigation.UserAction;
import jenkins.security.csp.Contributor;
import jenkins.security.csp.CspBuilder;
import jenkins.security.csp.Directive;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * This extension automatically allows loading images from the domain hosting the current user's avatar
 * as determined via {@link hudson.tasks.UserAvatarResolver}.
 */
// TODO Is this good enough? Or is there a user list showing other users' avatars?
@Restricted(NoExternalUse.class)
@Extension
public class UserAvatarContributor implements Contributor {

    public static final Logger LOGGER = Logger.getLogger(UserAvatarContributor.class.getName());

    @Override
    public void apply(CspBuilder cspBuilder) {
        User user = User.current();
        if (user == null) {
            return;
        }
        final String url = UserAvatarResolver.resolveOrNull(user, UserAction.AVATAR_SIZE);
        if (url == null) {
            LOGGER.log(Level.FINE, "No avatar image found for user " + user.getId());
            return;
        }
        try {
            final URI uri = new URI(url);
            final String host = uri.getHost();
            if (host == null) {
                // If there's no host, assume a local path
                LOGGER.log(Level.FINE, "Ignoring URI without host: " + url);
                return;
            }
            String cspValue = host;
            final String scheme = uri.getScheme();
            if (scheme != null) {
                cspValue = scheme + "://" + cspValue;
            }
            final int port = uri.getPort();
            if (port != -1) {
                cspValue = cspValue + ":" + port;
            }
            LOGGER.log(Level.FINER, "Allowing img-src '" + cspValue + "' from avatar uri: " + url);
            cspBuilder.add(Directive.IMG_SRC, cspValue);
        } catch (URISyntaxException e) {
            LOGGER.log(Level.FINE, "Failed to parse avatar URI: " + url, e);
        }
    }
}
