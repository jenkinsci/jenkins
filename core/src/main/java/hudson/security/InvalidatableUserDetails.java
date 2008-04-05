package hudson.security;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.userdetails.UserDetails;

import javax.servlet.http.HttpSession;

/**
 * {@link UserDetails} that can mark {@link Authentication} invalid.
 *
 * <p>
 * Tomcat persists sessions by using Java serialization (and
 * that includes the security token created by Acegi, which includes this object)
 * and when that happens, the next time the server comes back
 * it will try to deserialize {@link SecurityContext} that Acegi
 * puts into {@link HttpSession} (which transitively includes {@link UserDetails}
 * that can be implemented by Hudson.
 *
 * <p>
 * Such {@link UserDetails} implementation can override the {@link #isInvalid()}
 * method and return false, so that such {@link SecurityContext} will be
 * dropped before the rest of Acegi sees it.
 *
 * <p>
 * See https://hudson.dev.java.net/issues/show_bug.cgi?id=1482
 * 
 * @author Kohsuke Kawaguchi
 */
public interface InvalidatableUserDetails extends UserDetails {
    boolean isInvalid();
}
