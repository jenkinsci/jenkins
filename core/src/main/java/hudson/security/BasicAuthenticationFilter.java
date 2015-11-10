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
package hudson.security;

import hudson.model.User;
import jenkins.model.Jenkins;
import hudson.util.Scrambler;
import jenkins.security.ApiTokenProperty;
import org.acegisecurity.context.SecurityContextHolder;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;

/**
 * Implements the dual authentcation mechanism.
 *
 * <p>
 * Jenkins supports both the HTTP basic authentication and the form-based authentication.
 * The former is for scripted clients, and the latter is for humans. Unfortunately,
 * because the servlet spec does not allow us to programatically authenticate users,
 * we need to rely on some hack to make it work, and this is the class that implements
 * that hack.
 *
 * <p>
 * When an HTTP request arrives with an HTTP basic auth header, this filter detects
 * that and emulate an invocation of <tt>/j_security_check</tt>
 * (see <a href="http://mail-archives.apache.org/mod_mbox/tomcat-users/200105.mbox/%3C9005C0C9C85BD31181B20060085DAC8B10C8EF@tuvi.andmevara.ee%3E">this page</a> for the original technique.)
 *
 * <p>
 * This causes the container to perform authentication, but there's no way
 * to find out whether the user has been successfully authenticated or not.
 * So to find this out, we then redirect the user to
 * {@link jenkins.model.Jenkins#doSecured(StaplerRequest, StaplerResponse) <tt>/secured/...</tt> page}.
 *
 * <p>
 * The handler of the above URL checks if the user is authenticated,
 * and if not report an HTTP error code. Otherwise the user is
 * redirected back to the original URL, where the request is served.
 *
 * <p>
 * So all in all, the redirection works like <tt>/abc/def</tt> -> <tt>/secured/abc/def</tt>
 * -> <tt>/abc/def</tt>.
 *
 * <h2>Notes</h2>
 * <ul>
 * <li>
 * The technique of getting a request dispatcher for <tt>/j_security_check</tt> may not
 * work for all containers, but so far that seems like the only way to make this work.
 * <li>
 * This A->B->A redirect is a cyclic redirection, so we need to watch out for clients
 * that detect this as an error.
 * </ul> 
 *
 * @author Kohsuke Kawaguchi
 */
public class BasicAuthenticationFilter implements Filter {
    private ServletContext servletContext;

    public void init(FilterConfig filterConfig) throws ServletException {
        servletContext = filterConfig.getServletContext();
    }

    @SuppressWarnings("ACL.impersonate")
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse rsp = (HttpServletResponse) response;
        String authorization = req.getHeader("Authorization");

        String path = req.getServletPath();
        if(authorization==null || req.getUserPrincipal() !=null || path.startsWith("/secured/")
        || !Jenkins.getInstance().isUseSecurity()) {
            // normal requests, or security not enabled
            if(req.getUserPrincipal()!=null) {
                // before we route this request, integrate the container authentication
                // to Acegi. For anonymous users that doesn't have user principal,
                // AnonymousProcessingFilter that follows this should create
                // an Authentication object.
                SecurityContextHolder.getContext().setAuthentication(new ContainerAuthentication(req));
            }
            try {
                chain.doFilter(request,response);
            } finally {
                SecurityContextHolder.clearContext();
            }
            return;
        }

        // authenticate the user
        String username = null;
        String password = null;
        String uidpassword = Scrambler.descramble(authorization.substring(6));
        int idx = uidpassword.indexOf(':');
        if (idx >= 0) {
            username = uidpassword.substring(0, idx);
            password = uidpassword.substring(idx+1);
        }

        if(username==null) {
            rsp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            rsp.setHeader("WWW-Authenticate","Basic realm=\"Jenkins user\"");
            return;
        }

        {// attempt to authenticate as API token
            User u = User.get(username);
            ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
            if (t!=null && t.matchesPassword(password)) {
                SecurityContextHolder.getContext().setAuthentication(u.impersonate());
                try {
                    chain.doFilter(request,response);
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return;
            }
        }


        path = req.getContextPath()+"/secured"+path;
        String q = req.getQueryString();
        if(q!=null)
            path += '?'+q;

        // prepare a redirect
        rsp.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        rsp.setHeader("Location",path);

        // ... but first let the container authenticate this request
        RequestDispatcher d = servletContext.getRequestDispatcher("/j_security_check?j_username="+
            URLEncoder.encode(username,"UTF-8")+"&j_password="+URLEncoder.encode(password,"UTF-8"));
        d.include(req,rsp);
    }

    //public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    //    HttpServletRequest req = (HttpServletRequest) request;
    //    String authorization = req.getHeader("Authorization");
    //
    //    String path = req.getServletPath();
    //    if(authorization==null || req.getUserPrincipal()!=null || path.startsWith("/secured/")) {
    //        chain.doFilter(request,response);
    //    } else {
    //        if(req.getQueryString()!=null)
    //            path += req.getQueryString();
    //        ((HttpServletResponse)response).sendRedirect(req.getContextPath()+"/secured"+path);
    //    }
    //}

    public void destroy() {
    }
}
