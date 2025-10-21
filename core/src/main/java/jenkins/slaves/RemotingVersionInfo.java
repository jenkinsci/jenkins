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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.VersionNumber;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.agents.ControllerToAgentCallable;

/**
 * Provides information about Remoting versions used within the core.
 * @author Oleg Nenashev
 * @since unrestricted since 2.104, initially added in 2.100.
 */
public class RemotingVersionInfo {

    private static final Logger LOGGER = Logger.getLogger(RemotingVersionInfo.class.getName());
    private static final String RESOURCE_NAME = "remoting-info.properties";

    @NonNull
    private static VersionNumber EMBEDDED_VERSION;

    @NonNull
    private static VersionNumber MINIMUM_SUPPORTED_VERSION;

    private RemotingVersionInfo() {}

    static {
        Properties props = new Properties();
        try (InputStream is = RemotingVersionInfo.class.getResourceAsStream(RESOURCE_NAME)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load Remoting Info from " + RESOURCE_NAME, e);
        }

        EMBEDDED_VERSION = extractVersion(props, "remoting.embedded.version");
        MINIMUM_SUPPORTED_VERSION = extractVersion(props, "remoting.minimum.supported.version");
    }

    @NonNull
    private static VersionNumber extractVersion(@NonNull Properties props, @NonNull String propertyName)
            throws ExceptionInInitializerError {
        String prop = props.getProperty(propertyName);
        if (prop == null) {
            throw new ExceptionInInitializerError(String.format(
                    "Property %s is not defined in %s", propertyName, RESOURCE_NAME));
        }

        if (prop.contains("${")) { // Due to whatever reason, Maven does not nullify them
            throw new ExceptionInInitializerError(String.format(
                    "Property %s in %s has unresolved variable(s). Raw value: %s",
                    propertyName, RESOURCE_NAME, prop));
        }

        try {
            return new VersionNumber(prop);
        } catch (RuntimeException ex) {
            throw new ExceptionInInitializerError(new IOException(
                    String.format("Failed to parse version for for property %s in %s. Raw Value: %s",
                    propertyName, RESOURCE_NAME, prop), ex));
        }
    }

    /**
     * Returns a version which is embedded into the Jenkins core.
     * Note that this version <b>may</b> differ from one which is being really used in Jenkins.
     * @return Remoting version
     */
    @NonNull
    public static VersionNumber getEmbeddedVersion() {
        return EMBEDDED_VERSION;
    }

    /**
     * Gets Remoting version which is supported by the core.
     * Jenkins core and plugins make invoke operations on agents (e.g. {@link ControllerToAgentCallable})
     * and use Remoting-internal API within them.
     * In such case this API should be present on the remote side.
     * This method defines a minimum expected version, so that all calls should use a compatible API.
     * @return Minimal Remoting version for API calls.
     */
    @NonNull
    public static VersionNumber getMinimumSupportedVersion() {
        return MINIMUM_SUPPORTED_VERSION;
    }
}
