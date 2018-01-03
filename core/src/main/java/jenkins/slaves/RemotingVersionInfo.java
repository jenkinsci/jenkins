/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package jenkins.slaves;

import hudson.util.VersionNumber;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO: Make the API public (JENKINS-48766)
/**
 * Provides information about Remoting versions used withing the core.
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class RemotingVersionInfo {

    private static final Logger LOGGER = Logger.getLogger(RemotingVersionInfo.class.getName());
    private static final String RESOURCE_NAME="remoting-info.properties";

    @CheckForNull
    private static VersionNumber EMBEDDED_VERSION;

    @CheckForNull
    private static VersionNumber MINIMAL_SUPPORTED_VERSION;

    private RemotingVersionInfo() {}

    static {
        Properties props = new Properties();
        try (InputStream is = RemotingVersionInfo.class.getResourceAsStream(RESOURCE_NAME)) {
            if(is!=null) {
                props.load(is);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load Remoting Info from " + RESOURCE_NAME, e);
        }

        EMBEDDED_VERSION = tryExtractVersion(props, "remoting.embedded.version");
        MINIMAL_SUPPORTED_VERSION = tryExtractVersion(props, "remoting.minimal.supported.version");
    }

    @CheckForNull
    private static VersionNumber tryExtractVersion(@Nonnull Properties props, @Nonnull String propertyName) {
        String prop = props.getProperty(propertyName);
        if (prop == null) {
            LOGGER.log(Level.FINE, "Property {0} is not defined in {1}", new Object[] {propertyName, RESOURCE_NAME});
            return null;
        }

        if(prop.contains("${")) { // Due to whatever reason, Maven does not nullify them
            LOGGER.log(Level.WARNING, "Property {0} in {1} has unresolved variable(s). Raw value: {2}",
                    new Object[] {propertyName, RESOURCE_NAME, prop});
            return null;
        }

        try {
            return new VersionNumber(prop);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, String.format("Failed to parse version for for property %s in %s. Raw Value: %s",
                    propertyName, RESOURCE_NAME, prop), ex);
            return null;
        }
    }

    @CheckForNull
    public static VersionNumber getEmbeddedVersion() {
        return EMBEDDED_VERSION;
    }

    @CheckForNull
    public static VersionNumber getMinimalSupportedVersion() {
        return MINIMAL_SUPPORTED_VERSION;
    }
}
