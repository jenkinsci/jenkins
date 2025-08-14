package hudson.security;

import io.jenkins.servlet.http.HttpServletResponseWrapper;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletResponse;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;

/**
 * {@link AccessDeniedException} with more information.
 * @author Kohsuke Kawaguchi
 * @deprecated use {@link AccessDeniedException3}
 */
@Deprecated
public class AccessDeniedException2 extends AccessDeniedException {

    /**
     * This object represents the user being authenticated.
     */
    public final Authentication authentication;

    /**
     * This object represents the permission that the user needed.
     */
    public final Permission permission;

    public AccessDeniedException2(Authentication authentication, Permission permission) {
        this(null, authentication, permission);
    }

    public AccessDeniedException2(Throwable t, Authentication authentication, Permission permission) {
        super(Messages.AccessDeniedException2_MissingPermission(authentication.getName(),
                permission.group.title + "/" + permission.name), t);
        this.authentication = authentication;
        this.permission = permission;
    }

    /**
     * Reports the details of the access failure in HTTP headers to assist diagnosis.
     */
    public void reportAsHeaders(HttpServletResponse rsp) {
        toSpring().reportAsHeaders(HttpServletResponseWrapper.toJakartaHttpServletResponse(rsp));
    }

    /**
     * Reports the details of the access failure.
     * This method is similar to {@link #reportAsHeaders(HttpServletResponse)} for the intention
     * but instead of using HTTP headers, this version is meant to go inside the payload.
     */
    public void report(PrintWriter w) {
        toSpring().report(w);
    }

    @Override
    public AccessDeniedException3 toSpring() {
        Throwable t = getCause();
        return t != null ? new AccessDeniedException3(t, authentication.toSpring(), permission) : new AccessDeniedException3(authentication.toSpring(), permission);
    }
}
