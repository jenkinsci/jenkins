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

import java.io.File;
import java.io.FileOutputStream;
import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Test;
import org.xml.sax.SAXException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
 
/**
 * Tests of {@link PluginManager}.
 */
public class PluginManagerTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void parseRequestedPlugins() throws Exception {
        assertEquals("{other=2.0, stuff=1.2}", new LocalPluginManager(tmp.getRoot())
                .parseRequestedPlugins(new StringInputStream("<root><stuff plugin='stuff@1.0'><more plugin='other@2.0'><things plugin='stuff@1.2'/></more></stuff></root>")).toString());
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
        try {
            pluginManager.parseRequestedPlugins(new StringInputStream(evilXML));
            fail("XML contains an external entity, but no exception was thrown.");
        }
        catch (IOException ex) {
            assertThat(ex.getCause(), instanceOf(SAXException.class));
            assertThat(ex.getCause().getMessage(), containsString("Refusing to resolve entity with publicId(null) and systemId (file:///)"));
        }
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
    
    private static void assertAttribute(Manifest manifest, String attributeName, String value) throws AssertionError {
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
        File newFolder = tmp.newFolder("myJar");
        String manifestPath = "META-INF/MANIFEST.MF";
        new File("META-INF").mkdir();
        FileUtils.write(new File(newFolder, manifestPath), SAMPLE_MANIFEST_FILE);
        
        final File f = new File(tmp.getRoot(), "my.hpi");
        try(ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
            ZipEntry e = new ZipEntry(manifestPath);
            out.putNextEntry(e);
            byte[] data = SAMPLE_MANIFEST_FILE.getBytes();
            out.write(data, 0, data.length);
            out.closeEntry();
        }
        return f;
    }
        
    
    private URL toManifestUrl(File jarFile) throws MalformedURLException {
        final String manifestPath = "META-INF/MANIFEST.MF";
        return new URL("jar:" + jarFile.toURI().toURL() + "!/" + manifestPath);
    }  
}
