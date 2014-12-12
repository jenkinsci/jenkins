package jenkins.security.s2m;

import hudson.Extension;
import hudson.remoting.Callable;
import org.jenkinsci.remoting.Role;
import org.jenkinsci.remoting.RoleSensitive;

import javax.inject.Inject;
import java.util.Collection;

/**
 * Whitelists {@link Callable}s that are approved by the admins.
 *
 *
 * <p>
 * Smaller ordinal value allows other programmable {@link CallableWhitelist} to accept/reject
 * {@link Callable}s without bothering the admins. This impl should be used only for those
 * {@link Callable}s that our program does not have any idea for.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=-100)
public class AdminCallableWhitelist extends CallableWhitelist {
    @Inject
    AdminWhitelistRule rule;

    @Override
    public boolean isWhitelisted(RoleSensitive subject, Collection<Role> expected, Object context) {
        return rule.isWhitelisted(subject,expected,context);
    }
}
