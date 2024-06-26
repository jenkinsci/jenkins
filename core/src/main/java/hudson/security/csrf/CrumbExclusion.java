/*
 * Copyright (c) 2011 CloudBees, Inc.
 * All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.security.csrf;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Allows plugins to define exceptions to the CSRF protection filter.
 *
 * Please note that Jenkins 2.96 and newer accepts HTTP POST requests without CSRF crumb, if
 * HTTP Basic authentication uses an API token instead of a password, so many use cases
 * (simple API clients that support authentication but not obtaining a crumb) should be obsolete.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.446
 */
public abstract class CrumbExclusion implements ExtensionPoint {
    /**
     * This method is called for every incoming POST request.
     *
     * @return
     *      true to indicate that the callee had processed this request
     *      (for example by reporting an error, or by executing the rest of the chain.)
     */
    public /* abstract */ boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (Util.isOverridden(
                CrumbExclusion.class,
                getClass(),
                "process",
                javax.servlet.http.HttpServletRequest.class,
                javax.servlet.http.HttpServletResponse.class,
                javax.servlet.FilterChain.class)) {
            try {
                return process(
                        javax.servlet.http.HttpServletRequest.fromJakartaHttpServletRequest(request),
                        javax.servlet.http.HttpServletResponse.fromJakartaHttpServletResponse(response),
                        javax.servlet.FilterChain.fromJakartaFilterChain(chain));
            } catch (javax.servlet.ServletException e) {
                throw e.toJakartaServletException();
            }
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + CrumbExclusion.class.getSimpleName() + ".process methods");
        }
    }

    /**
     * @deprecated use {@link #process(HttpServletRequest, HttpServletResponse, FilterChain)}
     */
    @Deprecated
    public boolean process(
            javax.servlet.http.HttpServletRequest request,
            javax.servlet.http.HttpServletResponse response,
            javax.servlet.FilterChain chain)
            throws IOException, javax.servlet.ServletException {
        if (Util.isOverridden(
                CrumbExclusion.class,
                getClass(),
                "process",
                HttpServletRequest.class,
                HttpServletResponse.class,
                FilterChain.class)) {
            try {
                return process(
                        request.toJakartaHttpServletRequest(),
                        response.toJakartaHttpServletResponse(),
                        chain.toJakartaFilterChain());
            } catch (ServletException e) {
                throw javax.servlet.ServletException.fromJakartaServletException(e);
            }
        } else {
            throw new AbstractMethodError("The class " + getClass().getName() + " must override at least one of the "
                    + CrumbExclusion.class.getSimpleName() + ".process methods");
        }
    }

    public static ExtensionList<CrumbExclusion> all() {
        return ExtensionList.lookup(CrumbExclusion.class);
    }
}
