/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
package jenkins.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jenkins.util.io.OnMaster;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * System properties provider, powered by {@link ServletContext}.
 * @author Oleg Nenashev
 * @see SystemProperties
 */
@Restricted(NoExternalUse.class)
public class ServletContextSystemPropertiesProvider extends SystemPropertiesProvider implements OnMaster, ServletContextListener {

    // this class implements ServletContextListener and is declared in WEB-INF/web.xml

    /**
     * The ServletContext to get the "init" parameters from.
     */
    @CheckForNull
    private static ServletContext theContext;

    /**
     * Called by the servlet container to initialize the {@link ServletContext}.
     */
    @Override
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
            justification = "Currently Jenkins instance may have one ond only one context")
    public void contextInitialized(ServletContextEvent event) {
        theContext = event.getServletContext();
        SystemPropertiesProvider.addProvider(this);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        theContext = null;
    }

    @CheckForNull
    @Override
    public String getProperty(@Nonnull String key) {
        return theContext != null ? theContext.getInitParameter(key) : null;
    }
}
