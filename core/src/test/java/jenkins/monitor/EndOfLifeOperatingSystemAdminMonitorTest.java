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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.junit.Test;

public class EndOfLifeOperatingSystemAdminMonitorTest {

    private final EndOfLifeOperatingSystemAdminMonitor monitor;
    private final Random random = new Random();
    private final String PREFIX = "administrativeMonitor/";

    public EndOfLifeOperatingSystemAdminMonitorTest() throws IOException {
        this.monitor = random.nextBoolean()
                ? new EndOfLifeOperatingSystemAdminMonitor()
                : new EndOfLifeOperatingSystemAdminMonitor(EndOfLifeOperatingSystemAdminMonitor.class.getName());
    }

    @Test
    public void testGetDisplayName() {
        assertThat(monitor.getDisplayName(), is("Operating system end of life monitor"));
    }

    @Test
    public void testGetUrl() {
        assertThat(monitor.getUrl(), is(PREFIX + monitor.getClass().getName()));
    }

    @Test
    public void testGetSearchUrl() {
        assertThat(monitor.getSearchUrl(), is(PREFIX + monitor.getClass().getName()));
    }

    // @Test - ignored while developing
    public void testIsActivated() throws IOException {
        // Will fail if operating system running the test is reaching end of life soon
        assertFalse(monitor.isActivated());
    }

    @Test
    public void testNotIsActivatedWhenIgnoreEndOfLife() throws IOException {
        monitor.ignoreEndOfLife = true;
        assertFalse(monitor.isActivated());
    }

    @Test
    public void testIsSecurity() throws IOException {
        assertFalse(monitor.isSecurity());
    }

    @Test
    public void testReadPrettyNameAlpine314() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.14").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Alpine Linux v3.14"), is("Alpine Linux v3.14"));
    }

    @Test
    public void testReadPrettyNameAlpine315() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.15").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Alpine Linux v3.15"), is("Alpine Linux v3.15"));
    }

    @Test
    public void testReadPrettyNameAlpine316() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.16").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Alpine Linux v3.16"), is("Alpine Linux v3.16"));
    }

    @Test
    public void testReadPrettyNameAlpine317() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.17").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Alpine Linux v3.17"), is("Alpine Linux v3.17"));
    }

    @Test
    public void testReadPrettyNameAlpine318() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-alpine-3.18").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Alpine Linux v3.18"), is("Alpine Linux v3.18"));
    }

    @Test
    public void testReadPrettyNameCentOS7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-centos-7").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "CentOS Linux.* 7"), is("CentOS Linux 7 (Core)"));
    }

    @Test
    public void testReadPrettyNameDebian10() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-debian-10").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Debian.* 10"), is("Debian GNU/Linux 10 (buster)"));
    }

    @Test
    public void testReadPrettyNameFedora36() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-fedora-36").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Fedora.* 36"), is("Fedora Linux 36 (Container Image)"));
    }

    @Test
    public void testReadPrettyNameFedora37() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-fedora-37").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Fedora.* 37"), is("Fedora Linux 37 (Container Image)"));
    }

    @Test
    public void testReadPrettyNameOracle7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-oracle-7").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Oracle Linux.* 7"), is("Oracle Linux Server 7.9"));
    }

    @Test
    public void testReadPrettyNameRedHat7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-redhat-7").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Red Hat Enterprise Linux.* 7"), is("Red Hat Enterprise Linux Server 7.9 (Maipo)"));
    }

    @Test
    public void testReadPrettyNameScientific7() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-scientific-7").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Scientific Linux.* 7"), is("Scientific Linux 7.9 (Nitrogen)"));
    }

    @Test
    public void testReadPrettyNameUbuntu18() throws Exception {
        File releaseFile = new File(this.getClass().getResource("os-release-ubuntu-18.04").toURI());
        assertThat(monitor.readPrettyName(releaseFile, "Ubuntu.* 18"), is("Ubuntu 18.04.6 LTS"));
    }

    @Test
    public void testReadPrettyNameMissingFile() {
        assertThat(monitor.readPrettyName(new File("/this/file/does/not/exist"), ".*"), is(""));
    }
}
