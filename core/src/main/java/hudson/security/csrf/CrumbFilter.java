/**
 * Copyright (c) 2008-2009 Yahoo! Inc. 
 * All rights reserved. 
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.security.csrf;

import hudson.util.MultipartFormDataParser;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Checks for and validates crumbs on requests that cause state changes, to
 * protect against cross site request forgeries.
 * 
 * @author dty
 */
public class CrumbFilter implements Filter {
    /**
     * Because servlet containers generally don't specify the ordering of the initialization
     * (and different implementations indeed do this differently --- See HUDSON-3878),
     * we cannot use Hudson to the CrumbIssuer into CrumbFilter eagerly.
     */
    public CrumbIssuer getCrumbIssuer() {
        Jenkins h = Jenkins.getInstance();
        if(h==null)     return null;    // before Jenkins is initialized?
        return h.getCrumbIssuer();
    }

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        CrumbIssuer crumbIssuer = getCrumbIssuer();
        if (crumbIssuer == null || !(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("POST".equals(httpRequest.getMethod())) {
            for (CrumbExclusion e : CrumbExclusion.all()) {
                if (e.process(httpRequest,httpResponse,chain))
                    return;
            }

            String crumbFieldName = crumbIssuer.getDescriptor().getCrumbRequestField();
            String crumbSalt = crumbIssuer.getDescriptor().getCrumbSalt();

            String crumb = httpRequest.getHeader(crumbFieldName);
            boolean valid = false;
            if (crumb == null) {
                Enumeration<?> paramNames = request.getParameterNames();
                while (paramNames.hasMoreElements()) {
                    String paramName = (String) paramNames.nextElement();
                    if (crumbFieldName.equals(paramName)) {
                        crumb = request.getParameter(paramName);
                        break;
                    }
                }
            }
            if (crumb != null) {
                if (crumbIssuer.validateCrumb(httpRequest, crumbSalt, crumb)) {
                    valid = true;
                } else {
                    LOGGER.log(Level.WARNING, "Found invalid crumb {0}.  Will check remaining parameters for a valid one...", crumb);
                }
            }
            // Multipart requests need to be handled by each handler.
            if (valid || isMultipart(httpRequest)) {
                chain.doFilter(request, response);
            } else {
                LOGGER.log(Level.WARNING, "No valid crumb was included in request for {0}. Returning {1}.", new Object[] {httpRequest.getRequestURI(), HttpServletResponse.SC_FORBIDDEN});
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,"No valid crumb was included in the request");
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    protected static boolean isMultipart(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        return MultipartFormDataParser.isMultipart(request.getContentType());
    }

    /**
     * {@inheritDoc}
     */
    public void destroy() {
    }

    private static final Logger LOGGER = Logger.getLogger(CrumbFilter.class.getName());
}
