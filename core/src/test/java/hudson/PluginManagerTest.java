/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.xml.sax.SAXException;

/**
 * Tests of {@link PluginManager}.
 */
public class PluginManagerTest {

    @TempDir Path tmp;

    @Test
    public void parseRequestedPlugins() throws Exception {
        Path output = Files.createFile(
                tmp.resolve("output.txt")
        );
        assertEquals("{other=2.0, stuff=1.2}", new LocalPluginManager(output.getParent().toFile())
                .parseRequestedPlugins(new ByteArrayInputStream("<root><stuff plugin='stuff@1.0'><more plugin='other@2.0'><things plugin='stuff@1.2'/></more></stuff></root>".getBytes(StandardCharsets.UTF_8))).toString());
    }

    @Issue("SECURITY-167")
    @Test
    public void parseInvalidRequestedPlugins() throws Exception {
        String evilXML = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<!DOCTYPE project[<!ENTITY foo SYSTEM \"file:///\">]>\n" +
                "<root>\n" +
                "  <stuff plugin='stuff@1.0'>\n" +
                "&foo;" +
                "    <more plugin='other@2.0'>\n" +
                "      <things plugin='stuff@1.2'/>\n" +
                "    </more>\n" +
                "  </stuff>\n" +
                "</root>\n";

        PluginManager pluginManager = new LocalPluginManager(Util.createTempDir());
        final IOException ex = assertThrows(IOException.class,
                () -> pluginManager.parseRequestedPlugins(new ByteArrayInputStream(evilXML.getBytes(StandardCharsets.UTF_8))),
                "XML contains an external entity, but no exception was thrown.");
        assertThat(ex.getCause(), instanceOf(SAXException.class));
        assertThat(ex.getCause().getMessage(), containsString("DOCTYPE"));
        assertThat(ex.getCause().getMessage(), containsString("http://apache.org/xml/features/disallow-doctype-decl"));
    }

    @Test
    public void shouldProperlyParseManifestFromJar() throws IOException {
        File jar = createHpiWithManifest();
        final Manifest manifest = PluginManager.parsePluginManifest(jar.toURI().toURL());

        assertThat("manifest should have been read from the sample", manifest, notNullValue());
        assertAttribute(manifest, "Created-By", "Apache Maven");
        assertAttribute(manifest, "Short-Name", "matrix-auth");

        // Multi-line entries
        assertAttribute(manifest, "Specification-Title", "Offers matrix-based security authorization strategies (global and per-project).");
        assertAttribute(manifest, "Url", "http://wiki.jenkins-ci.org/display/JENKINS/Matrix+Authorization+Strategy+Plugin");

        // Empty field
        assertAttribute(manifest, "Plugin-Developers", null);
    }

    @Test
    public void shouldProperlyRetrieveModificationDate() throws IOException {
        File jar = createHpiWithManifest();
        URL url = toManifestUrl(jar);
        assertThat("Manifest last modified date should be equal to the file date",
                PluginManager.getModificationDate(url),
                equalTo(jar.lastModified()));
    }


    @Test
    @Issue("JENKINS-70420")
    public void updateSiteURLCheckValidation() throws Exception {
        LocalPluginManager pm = new LocalPluginManager(tmp.toFile());

        assertThat("ftp urls are not acceptable", pm.checkUpdateSiteURL("ftp://foo/bar"),
                allOf(FormValidationMatcher.validationWithMessage(Kind.ERROR), hasProperty("message", containsString("invalid URL"))));
        assertThat("file urls to non files are not acceptable", pm.checkUpdateSiteURL(tmp.toUri().toURL().toString()),
                allOf(FormValidationMatcher.validationWithMessage(Kind.ERROR), hasProperty("message", containsString("Unable to connect to the URL"))));

        assertThat("invalid URLs do not cause a stack tracek", pm.checkUpdateSiteURL("sufslef3,r3;r99 3 l4i34"),
                allOf(FormValidationMatcher.validationWithMessage(Kind.ERROR), hasProperty("message", containsString("invalid URL"))));

        assertThat("empty url message", pm.checkUpdateSiteURL(""),
                allOf(FormValidationMatcher.validationWithMessage(Kind.ERROR), hasProperty("message", containsString("cannot be empty"))));
        assertThat("null url message", pm.checkUpdateSiteURL(""),
                allOf(FormValidationMatcher.validationWithMessage(Kind.ERROR), hasProperty("message", containsString("cannot be empty"))));

        // create a tempoary local file
        Path p = tmp.resolve("some.json");
        Files.writeString(tmp.resolve("some.json"), "{}");
        assertThat("file urls pointing to existing files work", pm.checkUpdateSiteURL(p.toUri().toURL().toString()),
                FormValidationMatcher.validationWithMessage(Kind.OK));

        assertThat("http urls with non existing servers", pm.checkUpdateSiteURL("https://bogus.example.com"),
                allOf(FormValidationMatcher.validationWithMessage(Kind.ERROR), hasProperty("message", containsString("Unable to connect to the URL"))));

        // starting a http server here is likely to be overkill and given this is the predominant use case is not so likely to regress.
        assertThat("main UC validates correctly", pm.checkUpdateSiteURL("https://updates.jenkins.io/update-center.json"),
                FormValidationMatcher.validationWithMessage(Kind.OK));

    }

    private static void assertAttribute(Manifest manifest, String attributeName, String value) {
        Attributes attributes = manifest.getMainAttributes();
        assertThat("Main attributes must not be empty", attributes, notNullValue());
        assertThat("Attribute '" + attributeName + "' does not match the sample",
                attributes.getValue(attributeName),
                equalTo(value));

    }

    private static final String SAMPLE_MANIFEST_FILE = "Manifest-Version: 1.0\n" +
                "Archiver-Version: Plexus Archiver\n" +
                "Created-By: Apache Maven\n" +
                "Built-By: jglick\n" +
                "Build-Jdk: 1.8.0_92\n" +
                "Extension-Name: matrix-auth\n" +
                "Specification-Title: \n" +
                " Offers matrix-based security \n" +
                " authorization strate\n" +
                " gies (global and per-project).\n" +
                "Implementation-Title: matrix-auth\n" +
                "Implementation-Version: 1.4\n" +
                "Group-Id: org.jenkins-ci.plugins\n" +
                "Short-Name: matrix-auth\n" +
                "Long-Name: Matrix Authorization Strategy Plugin\n" +
                "Url: http://wiki.jenkins-ci.org/display/JENKINS/Matrix+Authorization+S\n" +
                " trategy+Plugin\n" +
                "Plugin-Version: 1.4\n" +
                "Hudson-Version: 1.609.1\n" +
                "Jenkins-Version: 1.609.1\n" +
                "Plugin-Dependencies: icon-shim:2.0.3,cloudbees-folder:5.2.2;resolution\n" +
                " :=optional\n" +
                "Plugin-Developers: ";

    private File createHpiWithManifest() throws IOException {
        Path metaInf = tmp.resolve("META-INF");
        Files.createDirectory(metaInf);
        Files.writeString(metaInf.resolve("MANIFEST.MF"), SAMPLE_MANIFEST_FILE, StandardCharsets.UTF_8);

        final File f = new File(tmp.toFile(), "my.hpi");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(f.toPath()))) {
            ZipEntry e = new ZipEntry("META-INF/MANIFEST.MF");
            out.putNextEntry(e);
            byte[] data = SAMPLE_MANIFEST_FILE.getBytes(StandardCharsets.UTF_8);
            out.write(data, 0, data.length);
            out.closeEntry();
        }
        return f;
    }


    private URL toManifestUrl(File jarFile) throws MalformedURLException {
        final String manifestPath = "META-INF/MANIFEST.MF";
        return new URL("jar:" + jarFile.toURI().toURL() + "!/" + manifestPath);
    }

    private static class FormValidationMatcher extends TypeSafeDiagnosingMatcher<FormValidation> {

        private final Kind kind;

        private FormValidationMatcher(Kind kind) {
            this.kind = kind;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("FormValidation of type ").appendValue(kind);
        }

        @Override
        protected boolean matchesSafely(FormValidation item, Description mismatchDescription) {
            mismatchDescription.appendText("FormValidation of type ").appendValue(item.kind);
            return item.kind == kind;
        }

        static FormValidationMatcher validationWithMessage(Kind kind) {
            return new FormValidationMatcher(kind);
        }

    }
}
