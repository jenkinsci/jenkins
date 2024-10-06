/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import io.jenkins.servlet.http.HttpSessionEventWrapper;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

/**
 * {@link jakarta.servlet.http.HttpSessionListener} {@link ExtensionPoint} for Jenkins.
 * <p>
 * Allows plugins to listen to {@link HttpSession} lifecycle events.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @since 2.2
 */
@SuppressFBWarnings(value = "NM_SAME_SIMPLE_NAME_AS_INTERFACE", justification = "Should shadow HttpSessionListener")
public abstract class HttpSessionListener implements ExtensionPoint, jakarta.servlet.http.HttpSessionListener, javax.servlet.http.HttpSessionListener {

    /**
     * Get all of the {@link HttpSessionListener} implementations.
     * @return All of the {@link HttpSessionListener} implementations.
     */
    public static ExtensionList<HttpSessionListener> all() {
        return ExtensionList.lookup(HttpSessionListener.class);
    }

    @Override
    public void sessionCreated(HttpSessionEvent httpSessionEvent) {
        if (Util.isOverridden(HttpSessionListener.class, getClass(), "sessionCreated", javax.servlet.http.HttpSessionEvent.class)) {
            sessionCreated(HttpSessionEventWrapper.fromJakartaHttpSessionEvent(httpSessionEvent));
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent httpSessionEvent) {
        if (Util.isOverridden(HttpSessionListener.class, getClass(), "sessionCreated", javax.servlet.http.HttpSessionEvent.class)) {
            sessionCreated(HttpSessionEventWrapper.fromJakartaHttpSessionEvent(httpSessionEvent));
        }
    }

    /**
     * @deprecated use {@link #sessionCreated(HttpSessionEvent)}
     */
    @Deprecated
    @Override
    public void sessionCreated(javax.servlet.http.HttpSessionEvent httpSessionEvent) {
        if (Util.isOverridden(HttpSessionListener.class, getClass(), "sessionCreated", HttpSessionEvent.class)) {
            sessionCreated(HttpSessionEventWrapper.toJakartaHttpSessionEvent(httpSessionEvent));
        }
    }

    /**
     * @deprecated use {@link #sessionDestroyed(HttpSessionEvent)}
     */
    @Deprecated
    @Override
    public void sessionDestroyed(javax.servlet.http.HttpSessionEvent httpSessionEvent) {
        if (Util.isOverridden(HttpSessionListener.class, getClass(), "sessionDestroyed", HttpSessionEvent.class)) {
            sessionDestroyed(HttpSessionEventWrapper.toJakartaHttpSessionEvent(httpSessionEvent));
        }
    }
}
