/*
 * Copyright (c) 2008-2009 Yahoo! Inc.
 * All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.security.csrf;

import hudson.util.MultipartFormDataParser;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.MetaInfServices;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.ForwardToView;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

/**
 * Checks for and validates crumbs on requests that cause state changes, to
 * protect against cross site request forgeries.
 *
 * @author dty
 */
public class CrumbFilter implements Filter {
    /**
     * Because servlet containers generally don't specify the ordering of the initialization
     * (and different implementations indeed do this differently --- See JENKINS-3878),
     * we cannot use Hudson to the CrumbIssuer into CrumbFilter eagerly.
     */
    public CrumbIssuer getCrumbIssuer() {
        Jenkins h = Jenkins.getInstanceOrNull();
        if (h == null)     return null;    // before Jenkins is initialized?
        return h.getCrumbIssuer();
    }

    @Restricted(NoExternalUse.class)
    @MetaInfServices
    public static class ErrorCustomizer implements RequirePOST.ErrorCustomizer {
        @Override
        public ForwardToView getForwardView() {
            return new ForwardToView(CrumbFilter.class, "retry");
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    private static class Security1774ServletRequest extends HttpServletRequestWrapper {
        Security1774ServletRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getPathInfo() {
            // see Stapler#getServletPath
            return canonicalPath(getRequestURI().substring(getContextPath().length()));
        }


        // Copied from Stapler#canonicalPath
        private static String canonicalPath(String path) {
            List<String> r = new ArrayList<>(Arrays.asList(path.split("/+")));
            for (int i = 0; i < r.size(); ) {
                if (r.get(i).isEmpty() || r.get(i).equals(".")) {
                    // empty token occurs for example, "".split("/+") is [""]
                    r.remove(i);
                } else
                if (r.get(i).equals("..")) {
                    // i==0 means this is a broken URI.
                    r.remove(i);
                    if (i > 0) {
                        r.remove(i - 1);
                        i--;
                    }
                } else {
                    i++;
                }
            }

            StringBuilder buf = new StringBuilder();
            if (path.startsWith("/"))
                buf.append('/');
            boolean first = true;
            for (String token : r) {
                if (!first)     buf.append('/');
                else            first = false;
                buf.append(token);
            }
            // translation: if (path.endsWith("/") && !buf.endsWith("/"))
            if (path.endsWith("/") && (buf.isEmpty() || buf.charAt(buf.length() - 1) != '/'))
                buf.append('/');
            return buf.toString();
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        CrumbIssuer crumbIssuer = getCrumbIssuer();
        if (crumbIssuer == null || !(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("POST".equals(httpRequest.getMethod())) {
            HttpServletRequest wrappedRequest = UNPROCESSED_PATHINFO ? httpRequest : new Security1774ServletRequest(httpRequest);
            for (CrumbExclusion e : CrumbExclusion.all()) {
                if (e.process(wrappedRequest, httpResponse, chain))
                    return;
            }

            String crumbFieldName = crumbIssuer.getDescriptor().getCrumbRequestField();
            String crumbSalt = crumbIssuer.getDescriptor().getCrumbSalt();

            boolean valid = false;
            String crumb = extractCrumbFromRequest(httpRequest, crumbFieldName);
            if (crumb == null) {
                // compatibility for clients that hard-code the default crumb name up to Jenkins 1.TODO
                extractCrumbFromRequest(httpRequest, ".crumb");
            }

            // JENKINS-40344: Don't spam the log just because a session is expired
            Level level = Jenkins.getAuthentication2() instanceof AnonymousAuthenticationToken ? Level.FINE : Level.WARNING;

            if (crumb != null) {
                if (crumbIssuer.validateCrumb(httpRequest, crumbSalt, crumb)) {
                    valid = true;
                } else {
                    LOGGER.log(level, "Found invalid crumb {0}. If you are calling this URL with a script, please use the API Token instead. More information: https://www.jenkins.io/redirect/crumb-cannot-be-used-for-script", crumb);
                }
            }

            if (valid) {
                chain.doFilter(request, response);
            } else {
                LOGGER.log(level, "No valid crumb was included in request for {0} by {1}. Returning {2}.", new Object[] {httpRequest.getRequestURI(), Jenkins.getAuthentication2().getName(), HttpServletResponse.SC_FORBIDDEN});
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "No valid crumb was included in the request");
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private String extractCrumbFromRequest(HttpServletRequest httpRequest, String crumbFieldName) {
        String crumb = httpRequest.getHeader(crumbFieldName);
        if (crumb == null) {
            Enumeration<?> paramNames = httpRequest.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String paramName = (String) paramNames.nextElement();
                if (crumbFieldName.equals(paramName)) {
                    crumb = httpRequest.getParameter(paramName);
                    break;
                }
            }
        }
        return crumb;
    }

    protected static boolean isMultipart(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        return MultipartFormDataParser.isMultiPartForm(request.getContentType());
    }

    @Override
    public void destroy() {
    }

    static /* non-final for Groovy */ boolean UNPROCESSED_PATHINFO = SystemProperties.getBoolean(CrumbFilter.class.getName() + ".UNPROCESSED_PATHINFO");

    private static final Logger LOGGER = Logger.getLogger(CrumbFilter.class.getName());
}
