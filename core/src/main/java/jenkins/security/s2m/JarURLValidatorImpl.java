/*
 * The MIT License
 *
 * Copyright (c) 2024 CloudBees, Inc.
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

package jenkins.security.s2m;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.PluginManager;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.JarURLValidator;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.ChannelConfigurator;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@Deprecated
@Extension
public class JarURLValidatorImpl extends ChannelConfigurator implements JarURLValidator {

    public static final Logger LOGGER = Logger.getLogger(JarURLValidatorImpl.class.getName());

    @Override
    public void onChannelBuilding(ChannelBuilder builder, @Nullable Object context) {
        LOGGER.log(Level.CONFIG, () -> "Setting up JarURLValidatorImpl for context: " + context);
        builder.withProperty(JarURLValidator.class, this);
    }

    @Override
    public void validate(URL url) throws IOException {
        final String rejectAllProp = JarURLValidatorImpl.class.getName() + ".REJECT_ALL";
        if (SystemProperties.getBoolean(rejectAllProp)) {
            LOGGER.log(Level.FINE, () -> "Rejecting URL due to configuration: " + url);
            throw new IOException("The system property '" + rejectAllProp + "' has been set, so all attempts by agents to load jars from the controller are rejected."
                    + " Update the agent.jar of the affected agent to a version released in August 2024 or later to prevent this error."); // TODO better version spec
        }
        final String allowAllProp = Channel.class.getName() + ".DISABLE_JAR_URL_VALIDATOR";
        if (SystemProperties.getBoolean(allowAllProp)) {
            LOGGER.log(Level.FINE, () -> "Allowing URL due to configuration: " + url);
            return;
        }
        if (!isAllowedJar(url)) {
            LOGGER.log(Level.FINE, () -> "Rejecting URL: " + url);
            throw new IOException("This URL does not point to a jar file allowed to be requested by agents: " + url + "."
                    + " Update the agent.jar of the affected agent to a version released in August 2024 or later to prevent this error."
            + " Alternatively, set the system property '" + allowAllProp + "' to 'true' if all the code built by Jenkins is as trusted as an administrator.");
        } else {
            LOGGER.log(Level.FINE, () -> "Allowing URL: " + url);
        }
    }

    @SuppressFBWarnings(
            value = "DMI_COLLECTION_OF_URLS",
            justification = "All URLs point to local files, so no DNS lookup.")
    private static boolean isAllowedJar(URL url) {
        final ClassLoader classLoader = Jenkins.get().getPluginManager().uberClassLoader;
        if (classLoader instanceof PluginManager.UberClassLoader uberClassLoader) {
            if (uberClassLoader.isPluginJar(url)) {
                LOGGER.log(Level.FINER, () -> "Determined to be plugin jar: " + url);
                return true;
            }
        }

        final ClassLoader coreClassLoader = Jenkins.class.getClassLoader();
        if (coreClassLoader instanceof URLClassLoader urlClassLoader) {
            if (Set.of(urlClassLoader.getURLs()).contains(url)) {
                LOGGER.log(Level.FINER, () -> "Determined to be core jar: " + url);
                return true;
            }
        }

        LOGGER.log(Level.FINER, () -> "Neither core nor plugin jar: " + url);
        return false;
    }
}
