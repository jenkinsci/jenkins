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
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    private static Stream<Arguments> testReadDocumentationUrls() {
        return Stream.of(
            Arguments.of("os-release-alma-8", "AlmaLinux.* 8", "AlmaLinux-8.7-Stone-Smilodon"),
            Arguments.of("os-release-alpine-3.14", "Alpine Linux v3.14", "Alpine-Linux-v3.14"),
            Arguments.of("os-release-alpine-3.15", "Alpine Linux v3.15", "Alpine-Linux-v3.15"),
            Arguments.of("os-release-alpine-3.16", "Alpine Linux v3.16", "Alpine-Linux-v3.16"),
            Arguments.of("os-release-alpine-3.17", "Alpine Linux v3.17", "Alpine-Linux-v3.17"),
            Arguments.of("os-release-alpine-3.18", "Alpine Linux v3.18", "Alpine-Linux-v3.18"),
            Arguments.of("os-release-centos-7", "CentOS Linux.* 7", "CentOS-Linux-7-Core"),
            Arguments.of("os-release-debian-10", "Debian.* 10", "Debian-GNU-Linux-10-buster"),
            Arguments.of("os-release-fedora-36", "Fedora.* 36", "Fedora-Linux-36-Container-Image"),
            Arguments.of("os-release-fedora-37", "Fedora.* 37", "Fedora-Linux-37-Container-Image"),
            Arguments.of("os-release-oracle-7", "Oracle Linux.* 7", "Oracle-Linux-Server-7.9"),
            Arguments.of("os-release-oracle-8", "Oracle Linux.* 8", "Oracle-Linux-Server-8.7"),
            Arguments.of("os-release-redhat-7", "Red Hat Enterprise Linux.* 7", "Red-Hat-Enterprise-Linux-Server-7.9-Maipo"),
            Arguments.of("os-release-redhat-8", "Red Hat Enterprise Linux.* 8", "Red-Hat-Enterprise-Linux-8.8-Ootpa"),
            Arguments.of("os-release-rocky-8", "Rocky Linux.* 8", "Rocky-Linux-8.7-Green-Obsidian"),
            Arguments.of("os-release-scientific-7", "Scientific Linux.* 7", "Scientific-Linux-7.9-Nitrogen"),
            Arguments.of("os-release-ubuntu-18.04", "Ubuntu.* 18", "Ubuntu-18.04.6-LTS")
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testReadDocumentationUrls(String fileName, String pattern, String component) throws Exception {
        File releaseFile = new File(this.getClass().getResource(fileName).toURI());
        assertThat(monitor.readDocumentationUrl(releaseFile, pattern), is(docsUrl(component)));
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

    private static Stream<Arguments> testReadOperatingSystemNames() {
        return Stream.of(
            Arguments.of("os-release-alma-8", "AlmaLinux.* 8", "AlmaLinux 8.7 (Stone Smilodon)"),
            Arguments.of("os-release-alpine-3.14", "Alpine Linux v3.14", "Alpine Linux v3.14"),
            Arguments.of("os-release-alpine-3.15", "Alpine Linux v3.15", "Alpine Linux v3.15"),
            Arguments.of("os-release-alpine-3.16", "Alpine Linux v3.16", "Alpine Linux v3.16"),
            Arguments.of("os-release-alpine-3.17", "Alpine Linux v3.17", "Alpine Linux v3.17"),
            Arguments.of("os-release-alpine-3.18", "Alpine Linux v3.18", "Alpine Linux v3.18"),
            Arguments.of("os-release-centos-7", "CentOS Linux.* 7", "CentOS Linux 7 (Core)"),
            Arguments.of("os-release-debian-10", "Debian.* 10", "Debian GNU/Linux 10 (buster)"),
            Arguments.of("os-release-fedora-36", "Fedora.* 36", "Fedora Linux 36 (Container Image)"),
            Arguments.of("os-release-fedora-37", "Fedora.* 37", "Fedora Linux 37 (Container Image)"),
            Arguments.of("os-release-oracle-7", "Oracle Linux.* 7", "Oracle Linux Server 7.9"),
            Arguments.of("os-release-oracle-8", "Oracle Linux.* 8", "Oracle Linux Server 8.7"),
            Arguments.of("os-release-redhat-7", "Red Hat Enterprise Linux.* 7", "Red Hat Enterprise Linux Server 7.9 (Maipo)"),
            Arguments.of("os-release-redhat-8", "Red Hat Enterprise Linux.* 8", "Red Hat Enterprise Linux 8.8 (Ootpa)"),
            Arguments.of("os-release-rocky-8", "Rocky Linux.* 8", "Rocky Linux 8.7 (Green Obsidian)"),
            Arguments.of("os-release-scientific-7", "Scientific Linux.* 7", "Scientific Linux 7.9 (Nitrogen)"),
            Arguments.of("os-release-ubuntu-18.04", "Ubuntu.* 18", "Ubuntu 18.04.6 LTS")
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testReadOperatingSystemNames(String fileName, String pattern, String job) throws Exception {
        File releaseFile = new File(this.getClass().getResource(fileName).toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, pattern), is(job));
    }

    @Test
    public void testReadOperatingSystemNameMissingFile() {
        assertThat(monitor.readOperatingSystemName(new File("/this/file/does/not/exist"), ".*"), is(""));
    }
}
