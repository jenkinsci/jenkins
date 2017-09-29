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

import hudson.ExtensionPoint;
import hudson.security.SecurityRealm;
import java.util.ArrayList;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Servlet {@link Filter} that chains multiple {@link Filter}s, provided by plugins
 *
 * <p>
 * While this class by itself is not an extension point, I'm marking this class
 * as an extension point so that this class will be more discoverable.
 *
 * <p>
 * {@link SecurityRealm} that wants to contribute {@link Filter}s should first
 * check if {@link SecurityRealm#createFilter(FilterConfig)} is more appropriate.
 *
 * @see SecurityRealm
 */
public class PluginServletFilter implements Filter, ExtensionPoint {
    private final List<Filter> list = new CopyOnWriteArrayList<Filter>();

    private /*almost final*/ FilterConfig config;

    /**
     * For backward compatibility with plugins that might register filters before Jenkins.getInstance()
     * starts functioning, when we are not sure which Jenkins instance a filter belongs to, put it here,
     * and let the first Jenkins instance take over.
     */
    private static final List<Filter> LEGACY = new Vector<Filter>();

    private static final String KEY = PluginServletFilter.class.getName();

    /**
     * Lookup the instance from servlet context.
     *
     * @param c the ServletContext most of the time taken from a Jenkins instance
     * @return get the current PluginServletFilter if it is already available
     */
    private static @CheckForNull PluginServletFilter getInstance(ServletContext c) {
        return (PluginServletFilter)c.getAttribute(KEY);
    }

    public void init(FilterConfig config) throws ServletException {
        this.config = config;
        synchronized (LEGACY) {
            list.addAll(LEGACY);
            LEGACY.clear();
        }
        for (Filter f : list) {
            f.init(config);
        }
        config.getServletContext().setAttribute(KEY,this);
    }

    public static void addFilter(Filter filter) throws ServletException {
        Jenkins j = Jenkins.getInstanceOrNull();
        
        PluginServletFilter container = null;
        if(j != null) {
            container = getInstance(j.servletContext);
	}
        // https://marvelution.atlassian.net/browse/JJI-188
        if (j==null || container == null) {
            // report who is doing legacy registration
            LOGGER.log(Level.WARNING, "Filter instance is registered too early: "+filter, new Exception());
            LEGACY.add(filter);
        } else {
            filter.init(container.config);
            container.list.add(filter);
        }
    }

    public static void removeFilter(Filter filter) throws ServletException {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j==null || getInstance(j.servletContext) == null) {
            LEGACY.remove(filter);
        } else {
            getInstance(j.servletContext).list.remove(filter);
        }
    }

    public void doFilter(ServletRequest request, ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        new FilterChain() {
            private final Iterator<Filter> itr = list.iterator();

            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                if(itr.hasNext()) {
                    // call next
                    itr.next().doFilter(request, response, this);
                } else {
                    // reached to the end
                    chain.doFilter(request,response);
                }
            }
        }.doFilter(request,response);
    }

    public void destroy() {
        for (Filter f : list) {
            f.destroy();
        }
        list.clear();
    }

    @Restricted(NoExternalUse.class)
    public static void cleanUp() {
        PluginServletFilter instance = getInstance(Jenkins.getInstance().servletContext);
        if (instance != null) {
            // While we could rely on the current implementation of list being a CopyOnWriteArrayList
            // safer to just take an explicit copy of the list and operate on the copy
            for (Filter f: new ArrayList<>(instance.list)) {
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
