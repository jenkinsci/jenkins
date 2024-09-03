package jenkins.security;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import io.jenkins.servlet.ServletExceptionWrapper;
import io.jenkins.servlet.http.HttpServletRequestWrapper;
import io.jenkins.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
        if (Util.isOverridden(BasicHeaderAuthenticator.class, getClass(), "authenticate2", javax.servlet.http.HttpServletRequest.class, javax.servlet.http.HttpServletResponse.class, String.class, String.class)) {
            try {
                return authenticate2(HttpServletRequestWrapper.fromJakartaHttpServletRequest(req), HttpServletResponseWrapper.fromJakartaHttpServletResponse(rsp), username, password);
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else if (Util.isOverridden(BasicHeaderAuthenticator.class, getClass(), "authenticate", javax.servlet.http.HttpServletRequest.class, javax.servlet.http.HttpServletResponse.class, String.class, String.class)) {
            try {
                org.acegisecurity.Authentication a = authenticate(HttpServletRequestWrapper.fromJakartaHttpServletRequest(req), HttpServletResponseWrapper.fromJakartaHttpServletResponse(rsp), username, password);
                return a != null ? a.toSpring() : null;
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the " + BasicHeaderAuthenticator.class.getSimpleName() + ".authenticate2 methods");
        }
    }

    /**
     * @deprecated use {@link #authenticate2(HttpServletRequest, HttpServletResponse, String, String)}
     * @since 2.266
     */
    @CheckForNull
    @Deprecated
    public Authentication authenticate2(javax.servlet.http.HttpServletRequest req, javax.servlet.http.HttpServletResponse rsp, String username, String password) throws IOException, javax.servlet.ServletException {
        if (Util.isOverridden(BasicHeaderAuthenticator.class, getClass(), "authenticate2", HttpServletRequest.class, HttpServletResponse.class, String.class, String.class)) {
            try {
                return authenticate2(HttpServletRequestWrapper.toJakartaHttpServletRequest(req), HttpServletResponseWrapper.toJakartaHttpServletResponse(rsp), username, password);
            } catch (ServletException e) {
                throw ServletExceptionWrapper.fromJakartaServletException(e);
            }
        } else if (Util.isOverridden(BasicHeaderAuthenticator.class, getClass(), "authenticate", javax.servlet.http.HttpServletRequest.class, javax.servlet.http.HttpServletResponse.class, String.class, String.class)) {
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
    public org.acegisecurity.Authentication authenticate(javax.servlet.http.HttpServletRequest req, javax.servlet.http.HttpServletResponse rsp, String username, String password) throws IOException, javax.servlet.ServletException {
        Authentication a = authenticate2(req, rsp, username, password);
        return a != null ? org.acegisecurity.Authentication.fromSpring(a) : null;
    }

    public static ExtensionList<BasicHeaderAuthenticator> all() {
        return ExtensionList.lookup(BasicHeaderAuthenticator.class);
    }
}
