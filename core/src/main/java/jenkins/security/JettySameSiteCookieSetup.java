/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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

package jenkins.security;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Set a {@link javax.servlet.ServletContext} attribute that instructs Jetty (typically via Winstone)
 * to set the {@code SameSite} attribute on cookies (typically session and Remember-me).
 */
// TODO Replace with Cookie#setAttribute once on Servlet 6+
@Restricted(NoExternalUse.class)
public class JettySameSiteCookieSetup {

    private static final Logger LOGGER = Logger.getLogger(JettySameSiteCookieSetup.class.getName());

    @Initializer(before = InitMilestone.COMPLETED, after = InitMilestone.PLUGINS_PREPARED) // 'after' to ensure loggers are set up
    public static void setUpCookie() {
        // https://eclipse.dev/jetty/javadoc/jetty-10/org/eclipse/jetty/http/HttpCookie.html#SAME_SITE_DEFAULT_ATTRIBUTE
        final String key = JettySameSiteCookieSetup.class.getName() + ".sameSiteDefault";
        if ("".equals(SystemProperties.getString(key))) {
            LOGGER.log(Level.CONFIG, "Not setting a SameSite default value for cookies in Jetty");
            return;
        }
        final String value = SystemProperties.getString(key, "Lax");
        Jenkins.get().getServletContext().setAttribute("org.eclipse.jetty.cookie.sameSiteDefault", value);
        LOGGER.log(Level.CONFIG, () -> "Setting SameSite default value for cookies in Jetty: " + value);
    }
}
