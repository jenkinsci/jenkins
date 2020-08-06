package jenkins.security;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

/**
 * When Jenkins receives HTTP basic authentication, this hook will validate the username/password
 * pair.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.576
 * @see BasicHeaderProcessor
 */
public abstract class BasicHeaderAuthenticator implements ExtensionPoint {
    /**
     * Given the parsed username and password field from the basic authentication header,
     * determine the effective security credential to process the request with.
     *
     * <p>
     * The method must return null if the password or username didn't match what's expected.
     * When null is returned, other authenticators will get a chance to process the request.
     * This is necessary because Jenkins accepts both real password as well as API tokens for the password.
     *
     * <p>
     * In contrast, when an exception is thrown the request processing will fail
     * immediately without providing a chance for other authenticators to process the request.
     *
     * <p>
     * When no processor can validate the username/password pair, caller will make
     * the request processing fail.
     * @since TODO
     */
    @CheckForNull
    public Authentication authenticate2(HttpServletRequest req, HttpServletResponse rsp, String username, String password) throws IOException, ServletException {
        if (Util.isOverridden(BasicHeaderAuthenticator.class, getClass(), "authenticate", HttpServletRequest.class, HttpServletResponse.class, String.class, String.class)) {
            org.acegisecurity.Authentication a = authenticate(req, rsp, username, password);
            return a != null ? a.toSpring() : null;
        } else {
            throw new AbstractMethodError("implement authenticate2");
        }
    }

    /**
     * @deprecated use {@link #authenticate2}
     */
    @Deprecated
    @CheckForNull
    public org.acegisecurity.Authentication authenticate(HttpServletRequest req, HttpServletResponse rsp, String username, String password) throws IOException, ServletException {
        Authentication a = authenticate2(req, rsp, username, password);
        return a != null ? org.acegisecurity.Authentication.fromSpring(a) : null;
    }

    public static ExtensionList<BasicHeaderAuthenticator> all() {
        return ExtensionList.lookup(BasicHeaderAuthenticator.class);
    }
}
