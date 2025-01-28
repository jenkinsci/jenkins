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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Random;
import java.util.stream.Stream;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.rules.TemporaryFolder;

public class OperatingSystemEndOfLifeAdminMonitorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

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
        return getArguments(true);
    }

    @ParameterizedTest
    @MethodSource
    public void testReadDocumentationUrls(String fileName, String pattern, String component) throws Exception {
        URL fileUrl = this.getClass().getResource(fileName);
        assertTrue("Resource file '" + fileName + "' not found", fileUrl != null);
        File releaseFile = new File(fileUrl.toURI());
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
        return getArguments(false);
    }

    @ParameterizedTest
    @MethodSource
    public void testReadOperatingSystemNames(String fileName, String pattern, String job) throws Exception {
        URL fileUrl = this.getClass().getResource(fileName);
        assertTrue("Resource file '" + fileName + "' not found", fileUrl != null);
        File releaseFile = new File(fileUrl.toURI());
        assertThat(monitor.readOperatingSystemName(releaseFile, pattern), is(job));
    }

    @Test
    public void testReadOperatingSystemListOnWarningDate() throws Exception {
        File dataFile = tmp.newFile();
        Files.writeString(dataFile.toPath(), "PRETTY_NAME=\"Test OS\"");
        JSONObject eolIn6Months = new JSONObject();
        eolIn6Months.put("pattern", "Test OS");
        eolIn6Months.put("endOfLife", LocalDate.now().plusMonths(6).toString());
        eolIn6Months.put("file", dataFile.getAbsolutePath());
        JSONArray jsonArray = new JSONArray();
        jsonArray.add(eolIn6Months);
        monitor.readOperatingSystemList(jsonArray.toString());
        assertTrue(monitor.isActivated());
        assertEquals(LocalDate.now().plusMonths(6).toString(), monitor.getEndOfLifeDate());
    }

    @Test
    public void testReadOperatingSystemNameMissingFile() {
        assertThat(monitor.readOperatingSystemName(new File("/this/file/does/not/exist"), ".*"), is(""));
    }

    private static String s(String fullString, boolean simplify) {
        if (!simplify) {
            return fullString;
        }
        return fullString.replace(" ", "-").replace("/", "-").replace("(", "").replace(")", "");
    }

    /**
     * Returns resource file nanme, pattern match for operating system
     * name, and expected value for each of the resource files used by
     * the test.
     *
     * @param simplify if true, then the expected value
     * is simplified by replacing ' ' with '-', by replacing '/' with
     * '-', and by removing '(' and ')'.
     * @return arguments for ParameterizedTest, resource file name,
     * pattern match for operating system name, and expected value
     */
    private static Stream<Arguments> getArguments(boolean simplify) {
        return Stream.of(
            Arguments.of("os-release-alma-8", "AlmaLinux.* 8.*", s("AlmaLinux 8.10 (Cerulean Leopard)", simplify)),
            Arguments.of("os-release-alma-9", "AlmaLinux.* 9.*", s("AlmaLinux 9.4 (Seafoam Ocelot)", simplify)),
            Arguments.of("os-release-alpine-3.14", "Alpine Linux v3.14", s("Alpine Linux v3.14", simplify)),
            Arguments.of("os-release-alpine-3.15", "Alpine Linux v3.15", s("Alpine Linux v3.15", simplify)),
            Arguments.of("os-release-alpine-3.16", "Alpine Linux v3.16", s("Alpine Linux v3.16", simplify)),
            Arguments.of("os-release-alpine-3.17", "Alpine Linux v3.17", s("Alpine Linux v3.17", simplify)),
            Arguments.of("os-release-alpine-3.18", "Alpine Linux v3.18", s("Alpine Linux v3.18", simplify)),
            Arguments.of("os-release-alpine-3.19", "Alpine Linux v3.19", s("Alpine Linux v3.19", simplify)),
            Arguments.of("os-release-alpine-3.20", "Alpine Linux v3.20", s("Alpine Linux v3.20", simplify)),
            Arguments.of("os-release-amazon-linux-2", "Amazon Linux 2", s("Amazon Linux 2", simplify)),
            Arguments.of("os-release-amazon-linux-2023", "Amazon Linux 2023.*", s("Amazon Linux 2023.5.20241001", simplify)),
            Arguments.of("os-release-centos-7", "CentOS Linux.* 7.*", s("CentOS Linux 7 (Core)", simplify)),
            Arguments.of("os-release-debian-10", "Debian.* 10.*", s("Debian GNU/Linux 10 (buster)", simplify)),
            Arguments.of("os-release-debian-11", "Debian.* 11.*", s("Debian GNU/Linux 11 (bullseye)", simplify)),
            Arguments.of("os-release-debian-12", "Debian.* 12.*", s("Debian GNU/Linux 12 (bookworm)", simplify)),
            Arguments.of("os-release-eurolinux-8", "EuroLinux.* 8.*", s("EuroLinux 8.10 (Bucharest)", simplify)),
            Arguments.of("os-release-eurolinux-9", "EuroLinux.* 9.*", s("EuroLinux 9.4 (San Marino)", simplify)),
            Arguments.of("os-release-fedora-36", "Fedora.* 36.*", s("Fedora Linux 36 (Container Image)", simplify)),
            Arguments.of("os-release-fedora-37", "Fedora.* 37.*", s("Fedora Linux 37 (Container Image)", simplify)),
            Arguments.of("os-release-fedora-38", "Fedora.* 38.*", s("Fedora Linux 38 (Container Image)", simplify)),
            Arguments.of("os-release-fedora-39", "Fedora.* 39.*", s("Fedora Linux 39 (Container Image)", simplify)),
            Arguments.of("os-release-fedora-39", "Fedora.* 39.*", s("Fedora Linux 39 (Container Image)", simplify)),
            Arguments.of("os-release-fedora-40", "Fedora.* 40.*", s("Fedora Linux 40 (Container Image)", simplify)),
            Arguments.of("os-release-fedora-41", "Fedora.* 41.*", s("Fedora Linux 41 (Container Image)", simplify)),
            Arguments.of("os-release-oracle-7", "Oracle Linux.* 7.*", s("Oracle Linux Server 7.9", simplify)),
            Arguments.of("os-release-oracle-8", "Oracle Linux.* 8.*", s("Oracle Linux Server 8.10", simplify)),
            Arguments.of("os-release-oracle-9", "Oracle Linux.* 9.*", s("Oracle Linux Server 9.4", simplify)),
            Arguments.of("os-release-redhat-7", "Red Hat Enterprise Linux.* 7.*", s("Red Hat Enterprise Linux Server 7.9 (Maipo)", simplify)),
            Arguments.of("os-release-redhat-8", "Red Hat Enterprise Linux.* 8.*", s("Red Hat Enterprise Linux 8.10 (Ootpa)", simplify)),
            Arguments.of("os-release-rocky-8", "Rocky Linux.* 8.*", s("Rocky Linux 8.10 (Green Obsidian)", simplify)),
            Arguments.of("os-release-rocky-9", "Rocky Linux.* 9.*", s("Rocky Linux 9.4 (Blue Onyx)", simplify)),
            Arguments.of("os-release-scientific-7", "Scientific Linux.* 7.*", s("Scientific Linux 7.9 (Nitrogen)", simplify)),
            Arguments.of("os-release-ubi-8", "Red Hat Enterprise Linux.* 8.*", s("Red Hat Enterprise Linux 8.10 (Ootpa)", simplify)),
            Arguments.of("os-release-ubi-9", "Red Hat Enterprise Linux.* 9.*", s("Red Hat Enterprise Linux 9.4 (Plow)", simplify)),
            Arguments.of("os-release-ubuntu-18.04", "Ubuntu.* 18.*", s("Ubuntu 18.04.6 LTS", simplify)),
            Arguments.of("os-release-ubuntu-20.04", "Ubuntu.* 20.*", s("Ubuntu 20.04.6 LTS", simplify)),
            Arguments.of("os-release-ubuntu-22.04", "Ubuntu.* 22.*", s("Ubuntu 22.04.4 LTS", simplify)),
            Arguments.of("os-release-ubuntu-24.04", "Ubuntu.* 24.*", s("Ubuntu 24.04.1 LTS", simplify))
        );
    }
}
