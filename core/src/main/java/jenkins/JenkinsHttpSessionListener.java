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
package jenkins;

import hudson.ExtensionList;
import jenkins.util.HttpSessionListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.servlet.http.HttpSessionEvent;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Web container hook for the {@link HttpSessionListener} {@link hudson.ExtensionPoint}.
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Restricted(NoExternalUse.class)
public final class JenkinsHttpSessionListener implements javax.servlet.http.HttpSessionListener {
    
    // TODO: Seems like classes like this should live in the /war/src/java
    // But that applies to a number of other classes too and it has never happened, so will
    // not do it with this class for now anyway.
    
    private static final Logger LOGGER = Logger.getLogger(JenkinsHttpSessionListener.class.getName());
    
    @Override
    public void sessionCreated(HttpSessionEvent httpSessionEvent) {
        for (HttpSessionListener listener : all()) {
            try {
                listener.sessionCreated(httpSessionEvent);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error calling HttpSessionListener ExtensionPoint sessionCreated().", e);
            }
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent httpSessionEvent) {
        for (HttpSessionListener listener : all()) {
            try {
                listener.sessionDestroyed(httpSessionEvent);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error calling HttpSessionListener ExtensionPoint sessionDestroyed().", e);
            }
        }
    }

    private static List<HttpSessionListener> all() {
        return ExtensionList.lookup(HttpSessionListener.class);
    }
}
