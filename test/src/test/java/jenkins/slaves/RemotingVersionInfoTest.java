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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@For(RemotingVersionInfo.class)
@WithJenkins
class RemotingVersionInfoTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-48766")
    void warShouldIncludeRemotingManifestEntries() throws Exception {
        ZipFile jenkinsWar = new ZipFile(new File(j.getWebAppRoot(), "../jenkins.war"));
        ZipEntry entry = new JarEntry("META-INF/MANIFEST.MF");
        try (InputStream inputStream = jenkinsWar.getInputStream(entry)) {
            assertNotNull(inputStream, "Cannot open input stream for /META-INF/MANIFEST.MF");
            Manifest manifest = new Manifest(inputStream);

            assertAttributeValue(manifest, "Remoting-Embedded-Version", RemotingVersionInfo.getEmbeddedVersion());
            assertAttributeValue(manifest, "Remoting-Minimum-Supported-Version", RemotingVersionInfo.getMinimumSupportedVersion());
        }
    }

    private void assertAttributeValue(Manifest manifest, String attributeName, Object expectedValue) {
        assertThat("Wrong value of manifest attribute " + attributeName,
                manifest.getMainAttributes().getValue(attributeName),
                equalTo(expectedValue.toString()));
    }
}
