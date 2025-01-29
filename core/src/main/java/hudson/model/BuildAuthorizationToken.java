/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.model;

import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;
import hudson.Util;
import hudson.security.ACL;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.springframework.security.access.AccessDeniedException;

/**
 * Authorization token to allow projects to trigger themselves under the secured environment.
 *
 * @author Kohsuke Kawaguchi
 * @see BuildableItem
 * @deprecated 2008-07-20
 *      Use {@link ACL} and {@link Item#BUILD}. This code is only here
 *      for the backward compatibility.
 */
@Deprecated
public final class BuildAuthorizationToken {
    private final String token;

    public BuildAuthorizationToken(String token) {
        this.token = token;
    }

    /**
     * @since 2.475
     */
    public static BuildAuthorizationToken create(StaplerRequest2 req) {
        if (req.getParameter("pseudoRemoteTrigger") != null) {
            String token = Util.fixEmpty(req.getParameter("authToken"));
            if (token != null)
                return new BuildAuthorizationToken(token);
        }

        return null;
    }

    /**
     * @deprecated use {@link #create(StaplerRequest2)}
     */
    @Deprecated
    public static BuildAuthorizationToken create(StaplerRequest req) {
        return create(StaplerRequest.toStaplerRequest2(req));
    }

    @Deprecated public static void checkPermission(AbstractProject<?, ?> project, BuildAuthorizationToken token, StaplerRequest req, StaplerResponse rsp) throws IOException {
        checkPermission((Job<?, ?>) project, token, StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    /**
     * @since 2.475
     */
    public static void checkPermission(Job<?, ?> project, BuildAuthorizationToken token, StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (token != null && token.token != null) {
            //check the provided token
            String providedToken = req.getParameter("token");
            if (providedToken != null && providedToken.equals(token.token))
                return;
            if (providedToken != null)
                throw new AccessDeniedException(Messages.BuildAuthorizationToken_InvalidTokenProvided());
        }

        project.checkPermission(Item.BUILD);

        if (req.getMethod().equals("POST")) {
            return;
        }

        rsp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        rsp.addHeader("Allow", "POST");
        throw HttpResponses.forwardToView(project, "requirePOST.jelly");
    }

    /**
     * @deprecated use {@link #checkPermission(Job, BuildAuthorizationToken, StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    public static void checkPermission(Job<?, ?> project, BuildAuthorizationToken token, StaplerRequest req, StaplerResponse rsp) throws IOException {
        checkPermission(project, token, StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    public String getToken() {
        return token;
    }

    public static final class ConverterImpl extends AbstractSingleValueConverter {
        @Override
        public boolean canConvert(Class type) {
            return type == BuildAuthorizationToken.class;
        }

        @Override
        public Object fromString(String str) {
            return new BuildAuthorizationToken(str);
        }

        @Override
        public String toString(Object obj) {
            return ((BuildAuthorizationToken) obj).token;
        }
    }
}
