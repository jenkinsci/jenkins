/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package jenkins.model.version;

import com.google.common.annotations.VisibleForTesting;
import hudson.Main;
import jenkins.model.Jenkins;
import jenkins.util.xml.XMLUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of VersionProvider that finds the Jenkins version from an included properties file or
 * falling back to the Jenkins pom file in {@linkplain Main#isDevelopmentMode development mode}.
 *
 * @since TODO
 */
public class DefaultVersionOracle implements VersionOracle {
    private static final Logger LOGGER = Logger.getLogger(DefaultVersionOracle.class.getName());

    @GuardedBy("this")
    private volatile String version;

    @Nonnull
    @Override
    public Optional<String> getVersion() {
        if (version == null) {
            synchronized (this) {
                if (version == null) {
                    Properties properties = loadJenkinsVersionProperties();
                    version = properties.getProperty("version", Jenkins.UNCOMPUTED_VERSION);
                    if (versionRequiresPomLookup(version)) {
                        version = findJenkinsPomFile()
                                .flatMap(DefaultVersionOracle::extractVersionFromPom)
                                .orElse(version);
                    }
                }
            }
        }
        return Optional.of(version);
    }

    private static Properties loadJenkinsVersionProperties() {
        Properties properties = new Properties();
        try (InputStream is = Jenkins.class.getResourceAsStream("jenkins-version.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not read jenkins-version.properties", e);
        }
        return properties;
    }

    private static boolean versionRequiresPomLookup(String version) {
        return Main.isDevelopmentMode && "${project.version}".equals(version);
    }

    private static Optional<File> findJenkinsPomFile() {
        try {
            for (File dir = new File(".").getAbsoluteFile(); dir != null; dir = dir.getParentFile()) {
                File pom = new File(dir, "pom.xml");
                if (pom.exists() && "pom".equals(XMLUtils.getValue("/project/artifactId", pom))) {
                    return Optional.of(pom.getCanonicalFile());
                }
            }
        } catch (IOException | XPathExpressionException | SAXException e) {
            LOGGER.log(Level.WARNING, "Could not read Jenkins pom", e);
        }
        return Optional.empty();
    }

    private static Optional<String> extractVersionFromPom(File pom) {
        LOGGER.info(() -> "Reading Jenkins version from " + pom.getAbsolutePath());
        try {
            return Optional.of(XMLUtils.getValue("/project/version", pom));
        } catch (IOException | XPathExpressionException | SAXException e) {
            LOGGER.log(Level.WARNING, e, () -> "Could not read Jenkins version from " + pom.getAbsolutePath());
            return Optional.empty();
        }
    }

    @Restricted(NoExternalUse.class)
    @VisibleForTesting
    public synchronized void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "DefaultVersionOracle{" + version + '}';
    }
}
