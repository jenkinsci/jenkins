/*
 * The MIT License
 *
 * Copyright 2023 Mark Waite.
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

package jenkins.monitor;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.junit.Test;

public class OperatingSystemEndOfLifeAdminMonitorTest {

    private final OperatingSystemEndOfLifeAdminMonitor monitor;
    private final Random random = new Random();
    private final String PREFIX = "administrativeMonitor/";

    public OperatingSystemEndOfLifeAdminMonitorTest() throws IOException {
        this.monitor = random.nextBoolean()
                ? new OperatingSystemEndOfLifeAdminMonitor()
                : new OperatingSystemEndOfLifeAdminMonitor(OperatingSystemEndOfLifeAdminMonitor.class.getName());
    }

    @Test
    public void testGetDisplayName() {
        assertThat(monitor.getDisplayName(), is("Operating system end of life monitor"));
    }

    @Test
    public void testGetAfterEndOfLifeDate() {
        assertFalse(monitor.getAfterEndOfLifeDate());
    }

    @Test
    public void testGetDocumentationUrl() {
        assertThat(monitor.getDocumentationUrl(), is(not(nullValue())));
    }

    @Test
    public void testGetEndOfLifeDate() {
        assertThat(monitor.getEndOfLifeDate(), is("2099-12-31"));
    }

    @Test
    public void testGetOperatingSystemName() {
        /* Operating system name depends on the operating system running test */
        assertThat(monitor.getOperatingSystemName(), not(nullValue()));
    }

    @Test
    public void testGetSearchUrl() {
        assertThat(monitor.getSearchUrl(), is(PREFIX + monitor.getClass().getName()));
    }

    @Test
    public void testGetUrl() {
        assertThat(monitor.getUrl(), is(PREFIX + monitor.getClass().getName()));
    }

    @Test
    public void testIsActivated() throws IOException {
        // Will fail if operating system running the test is reaching end of life soon
        assertFalse(monitor.isActivated());
    }

    @Test
    public void testIsSecurity() throws IOException {
        assertFalse(monitor.isSecurity());
    }

    @Test
    public void testNotIsActivatedWhenIgnoreEndOfLife() throws IOException {
        monitor.ignoreEndOfLife = true;
        assertFalse(monitor.isActivated());
    }

    private String docsUrl(String component) {
        return "https://www.jenkins.io/redirect/operating-system-end-of-life?q=" + component;
    }

    @Test
    public void testReadDocumentationUrlAlma8() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alma-8").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "AlmaLinux.* 8"), is(docsUrl("AlmaLinux-8.7-Stone-Smilodon")));
    }

    @Test
    public void testReadDocumentationUrlAlpine314() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.14").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Alpine Linux v3.14"), is(docsUrl("Alpine-Linux-v3.14")));
    }

    @Test
    public void testReadDocumentationUrlAlpine315() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.15").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Alpine Linux v3.15"), is(docsUrl("Alpine-Linux-v3.15")));
    }

    @Test
    public void testReadDocumentationUrlAlpine316() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.16").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Alpine Linux v3.16"), is(docsUrl("Alpine-Linux-v3.16")));
    }

    @Test
    public void testReadDocumentationUrlAlpine317() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.17").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Alpine Linux v3.17"), is(docsUrl("Alpine-Linux-v3.17")));
    }

    @Test
    public void testReadDocumentationUrlAlpine318() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.18").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Alpine Linux v3.18"), is(docsUrl("Alpine-Linux-v3.18")));
    }

    @Test
    public void testReadDocumentationUrlCentOS7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-centos-7").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "CentOS Linux.* 7"), is(docsUrl("CentOS-Linux-7-Core")));
    }

    @Test
    public void testReadDocumentationUrlDebian10() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-debian-10").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Debian.* 10"), is(docsUrl("Debian-GNU-Linux-10-buster")));
    }

    @Test
    public void testReadDocumentationUrlFedora36() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-fedora-36").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Fedora.* 36"), is(docsUrl("Fedora-Linux-36-Container-Image")));
    }

    @Test
    public void testReadDocumentationUrlFedora37() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-fedora-37").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Fedora.* 37"), is(docsUrl("Fedora-Linux-37-Container-Image")));
    }

    @Test
    public void testReadDocumentationUrlOracle7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-oracle-7").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Oracle Linux.* 7"), is(docsUrl("Oracle-Linux-Server-7.9")));
    }

    @Test
    public void testReadDocumentationUrlOracle8() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-oracle-8").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Oracle Linux.* 8"), is(docsUrl("Oracle-Linux-Server-8.7")));
    }

    @Test
    public void testReadDocumentationUrlRedHat7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-redhat-7").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Red Hat Enterprise Linux.* 7"), is(docsUrl("Red-Hat-Enterprise-Linux-Server-7.9-Maipo")));
    }

    @Test
    public void testReadDocumentationUrlRedHat8() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-redhat-8").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Red Hat Enterprise Linux.* 8"), is(docsUrl("Red-Hat-Enterprise-Linux-8.8-Ootpa")));
    }

    @Test
    public void testReadDocumentationUrlRocky8() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-rocky-8").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Rocky Linux.* 8"), is(docsUrl("Rocky-Linux-8.7-Green-Obsidian")));
    }

    @Test
    public void testReadDocumentationUrlScientific7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-scientific-7").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Scientific Linux.* 7"), is(docsUrl("Scientific-Linux-7.9-Nitrogen")));
    }

    @Test
    public void testReadDocumentationUrlUbuntu18() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-ubuntu-18.04").toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, "Ubuntu.* 18"), is(docsUrl("Ubuntu-18.04.6-LTS")));
    }

    @Test
    public void testReadOperatingSystemListEmptySet() {
        IOException e = assertThrows(IOException.class, () -> monitor.readOperatingSystemList("[]"));
        assertThat(e.getMessage(), is("Empty data set"));
    }

    @Test
    public void testReadOperatingSystemListNoEndOfLife() {
        IOException e = assertThrows(IOException.class, () -> monitor.readOperatingSystemList("[{\"pattern\": \"Alpine\"}]"));
        assertThat(e.getMessage(), is("No end of life date for Alpine"));
    }

    @Test
    public void testReadOperatingSystemListNoPattern() {
        IOException e = assertThrows(IOException.class, () -> monitor.readOperatingSystemList("[{\"endOfLife\": \"2029-03-31\"}]"));
        assertThat(e.getMessage(), is("Missing pattern in definition file"));
    }

    @Test
    public void testReadOperatingSystemNameAlma8() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alma-8").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "AlmaLinux.* 8"), is("AlmaLinux 8.7 (Stone Smilodon)"));
    }

    @Test
    public void testReadOperatingSystemNameAlpine314() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.14").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Alpine Linux v3.14"), is("Alpine Linux v3.14"));
    }

    @Test
    public void testReadOperatingSystemNameAlpine315() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.15").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Alpine Linux v3.15"), is("Alpine Linux v3.15"));
    }

    @Test
    public void testReadOperatingSystemNameAlpine316() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.16").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Alpine Linux v3.16"), is("Alpine Linux v3.16"));
    }

    @Test
    public void testReadOperatingSystemNameAlpine317() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.17").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Alpine Linux v3.17"), is("Alpine Linux v3.17"));
    }

    @Test
    public void testReadOperatingSystemNameAlpine318() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.18").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Alpine Linux v3.18"), is("Alpine Linux v3.18"));
    }

    @Test
    public void testReadOperatingSystemNameCentOS7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-centos-7").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "CentOS Linux.* 7"), is("CentOS Linux 7 (Core)"));
    }

    @Test
    public void testReadOperatingSystemNameDebian10() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-debian-10").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Debian.* 10"), is("Debian GNU/Linux 10 (buster)"));
    }

    @Test
    public void testReadOperatingSystemNameFedora36() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-fedora-36").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Fedora.* 36"), is("Fedora Linux 36 (Container Image)"));
    }

    @Test
    public void testReadOperatingSystemNameFedora37() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-fedora-37").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Fedora.* 37"), is("Fedora Linux 37 (Container Image)"));
    }

    @Test
    public void testReadOperatingSystemNameMissingFile() {
        assertThat(monitor.readOperatingSystemName(new File("/this/file/does/not/exist"), ".*"), is(""));
    }

    @Test
    public void testReadOperatingSystemNameOracle7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-oracle-7").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Oracle Linux.* 7"), is("Oracle Linux Server 7.9"));
    }

    @Test
    public void testReadOperatingSystemNameOracle8() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-oracle-8").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Oracle Linux.* 8"), is("Oracle Linux Server 8.7"));
    }

    @Test
    public void testReadOperatingSystemNameRedHat7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-redhat-7").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Red Hat Enterprise Linux.* 7"), is("Red Hat Enterprise Linux Server 7.9 (Maipo)"));
    }

    @Test
    public void testReadOperatingSystemNameRedHat8() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-redhat-8").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Red Hat Enterprise Linux.* 8"), is("Red Hat Enterprise Linux 8.8 (Ootpa)"));
    }

    @Test
    public void testReadOperatingSystemNameRocky8() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-rocky-8").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Rocky Linux.* 8"), is("Rocky Linux 8.7 (Green Obsidian)"));
    }

    @Test
    public void testReadOperatingSystemNameScientific7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-scientific-7").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Scientific Linux.* 7"), is("Scientific Linux 7.9 (Nitrogen)"));
    }

    @Test
    public void testReadOperatingSystemNameUbuntu18() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-ubuntu-18.04").toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, "Ubuntu.* 18"), is("Ubuntu 18.04.6 LTS"));
    }
}
