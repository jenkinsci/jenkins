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

import static jenkins.monitor.EndOfLifeDebian10AdminMonitor.BEGIN_DISPLAY_DATE;
import static jenkins.monitor.EndOfLifeDebian10AdminMonitor.DEPENDENCY_NAME;
import static jenkins.monitor.EndOfLifeDebian10AdminMonitor.END_OF_SUPPORT_DATE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.time.LocalDate;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class EndOfLifeDebian10AdminMonitorTest {

    private final EndOfLifeDebian10AdminMonitor monitor = new EndOfLifeDebian10AdminMonitor();

    public EndOfLifeDebian10AdminMonitorTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGetDependencyName() {
        assertThat(monitor.getDependencyName(), is(DEPENDENCY_NAME));
    }

    @Test
    public void testGetDisplayName() {
        assertThat(monitor.getDisplayName(), is("End of life for " + DEPENDENCY_NAME));
    }

    @Test
    public void testGetBeginDisplayDate() {
        assertThat(monitor.getBeginDisplayDate(), is(BEGIN_DISPLAY_DATE.toString()));
    }

    @Test
    public void testGetDocumentationURL() {
        assertThat(monitor.getDocumentationURL(), is(EndOfLifeAdminMonitor.DOCUMENTATION_URL));
    }

    @Test
    public void testIsUnsupported() {
        assertThat(monitor.isUnsupported(), is(LocalDate.now().isAfter(END_OF_SUPPORT_DATE)));
    }

    @Test
    public void testGetRequiredPermission() {
        assertThat(monitor.getRequiredPermission(), is(Jenkins.SYSTEM_READ));
    }

    @Test
    public void testWithFileContents() throws Exception {
        File osReleaseFile = new File(this.getClass().getResource("os-release-debian-10").toURI());
        EndOfLifeDebian10AdminMonitor testFileMonitor = new EndOfLifeDebian10AdminMonitor(osReleaseFile);
        assertThat(testFileMonitor.getBeginDisplayDate(), is(BEGIN_DISPLAY_DATE.toString()));
        assertThat(testFileMonitor.getDependencyName(), is(DEPENDENCY_NAME));
        assertThat(testFileMonitor.getDisplayName(), is("End of life for " + DEPENDENCY_NAME));
        assertThat(testFileMonitor.getRequiredPermission(), is(Jenkins.SYSTEM_READ));
        assertThat(testFileMonitor.isUnsupported(), is(LocalDate.now().isAfter(END_OF_SUPPORT_DATE)));
    }

    /**
     * Test of getEndOfSupportDate method, of class EndOfLifeDebian10AdminMonitor.
     */
    @Test
    public void testGetEndOfSupportDate() {
        System.out.println("getEndOfSupportDate");
        EndOfLifeDebian10AdminMonitor instance = new EndOfLifeDebian10AdminMonitor();
        String expResult = "";
        String result = instance.getEndOfSupportDate();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

}
