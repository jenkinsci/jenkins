/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

package hudson.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.security.SecurityRealm;
import io.jenkins.servlet.FilterWrapper;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.HttpServletFilter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.CompatibleFilter;

/**
 * Servlet {@link Filter} that chains multiple {@link Filter}s, provided by plugins.
 * <p>
 * In most cases you should rather use {@link HttpServletFilter}.
 * <p>
 * {@link SecurityRealm} that wants to contribute {@link Filter}s should first
 * check if {@link SecurityRealm#createFilter(FilterConfig)} is more appropriate.
 *
 * @see SecurityRealm
 */
public final class PluginServletFilter implements CompatibleFilter {
    private final List<Filter> list = new CopyOnWriteArrayList<>();

    private /*almost final*/ FilterConfig config;

    /**
     * For backward compatibility with plugins that might register filters before Jenkins.get()
     * starts functioning, when we are not sure which Jenkins instance a filter belongs to, put it here,
     * and let the first Jenkins instance take over.
     */
    private static final List<Filter> LEGACY = new Vector<>();

    private static final String KEY = PluginServletFilter.class.getName();

    /**
     * Lookup the instance from servlet context.
     *
     * @param c the ServletContext most of the time taken from a Jenkins instance
     * @return get the current PluginServletFilter if it is already available
     */
    private static @CheckForNull PluginServletFilter getInstance(ServletContext c) {
        return (PluginServletFilter) c.getAttribute(KEY);
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        this.config = config;
        synchronized (LEGACY) {
            list.addAll(LEGACY);
            LEGACY.clear();
        }
        for (Filter f : list) {
            f.init(config);
        }
        config.getServletContext().setAttribute(KEY, this);
    }

    /**
     * Dynamically register a new filter.
     * May be paired with {@link #removeFilter}.
     * <p>For most purposes you can instead use {@link HttpServletFilter}.
     *
     * @since 2.475
     */
    public static void addFilter(Filter filter) throws ServletException {
        Jenkins j = Jenkins.getInstanceOrNull();

        PluginServletFilter container = null;
        if (j != null) {
            container = getInstance(j.getServletContext());
        }
        // https://marvelution.atlassian.net/browse/JJI-188
        if (j == null || container == null) {
            // report who is doing legacy registration
            LOGGER.log(Level.WARNING, "Filter instance is registered too early: " + filter, new Exception());
            LEGACY.add(filter);
        } else {
            filter.init(container.config);
            container.list.add(filter);
        }
    }

    /**
     * @deprecated use {@link #addFilter(Filter)}
     */
    @Deprecated
    public static void addFilter(javax.servlet.Filter filter) throws javax.servlet.ServletException {
        try {
            addFilter(FilterWrapper.toJakartaFilter(filter));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    /**
     * Checks whether the given filter is already registered in the chain.
     * @param filter the filter to check.
     * @return true if the filter is already registered in the chain.
     * @since 2.475
     */
    public static boolean hasFilter(Filter filter) {
        Jenkins j = Jenkins.getInstanceOrNull();
        PluginServletFilter container = null;
        if (j != null) {
            container = getInstance(j.getServletContext());
        }
        if (j == null || container == null) {
            return LEGACY.contains(filter);
        } else {
            return container.list.contains(filter);
        }
    }

    /**
     * @deprecated use {@link #hasFilter(Filter)}
     * @since 2.94
     */
    @Deprecated
    public static boolean hasFilter(javax.servlet.Filter filter) {
        return hasFilter(FilterWrapper.toJakartaFilter(filter));
    }

    /**
     * @since 2.475
     */
    public static void removeFilter(Filter filter) throws ServletException {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null || getInstance(j.getServletContext()) == null) {
            LEGACY.remove(filter);
        } else {
            getInstance(j.getServletContext()).list.remove(filter);
        }
    }

    /**
     * @deprecated use {@link #removeFilter(Filter)}
     */
    @Deprecated
    public static void removeFilter(javax.servlet.Filter filter) throws javax.servlet.ServletException {
        try {
            removeFilter(FilterWrapper.toJakartaFilter(filter));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        new FilterChain() {
            private final Iterator<Filter> itr = list.iterator();

            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                if (itr.hasNext()) {
                    // call next
                    itr.next().doFilter(request, response, this);
                } else {
                    // reached to the end
                    chain.doFilter(request, response);
                }
            }
        }.doFilter(request, response);
    }

    @Override
    public void destroy() {
        for (Filter f : list) {
            f.destroy();
        }
        list.clear();
    }

    @Restricted(NoExternalUse.class)
    public static void cleanUp() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return;
        }
        PluginServletFilter instance = getInstance(jenkins.getServletContext());
        if (instance != null) {
            // While we could rely on the current implementation of list being a CopyOnWriteArrayList
            // safer to just take an explicit copy of the list and operate on the copy
            for (Filter f : new ArrayList<>(instance.list)) {
                instance.list.remove(f);
                // remove from the list even if destroy() fails as a failed destroy is still a destroy
                try {
                    f.destroy();
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Filter " + f + " propagated an exception from its destroy method",
                            e);
                } catch (Error e) {
                    throw e; // we are not supposed to catch errors, don't log as could be an OOM
                } catch (Throwable e) {
                    LOGGER.log(Level.SEVERE, "Filter " + f + " propagated an exception from its destroy method", e);
                }
            }
            // if some fool adds a filter while we are terminating, we should just log the fact
            if (!instance.list.isEmpty()) {
                LOGGER.log(Level.SEVERE, "The following filters appear to have been added during clean up: {0}",
                        instance.list);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PluginServletFilter.class.getName());
}
