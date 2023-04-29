/*
 * The MIT License
 *
 * Copyright 2023 mwaite.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.Test;

public class EndOfLifeAdminMonitorTest {

    private final String id = "my-identifier";
    private final String name = "dependency name";
    private final LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
    private final LocalDate yesterday = LocalDate.now().minusDays(1);
    private final String url = "https://www.jenkins.io/";
    private final File dataFile = new File("/etc/os-release");
    private final Pattern dataPattern = Pattern.compile(".* [0-9].*");

    private final LocalDate tomorrow = LocalDate.now().plusDays(1);
    private final LocalDate nextWeek = LocalDate.now().plusDays(7);

    private final EndOfLifeAdminMonitor monitor = new EndOfLifeAdminMonitor(id, name, threeDaysAgo, yesterday, url, dataFile, dataPattern);

    public EndOfLifeAdminMonitorTest() {
    }

    @Test
    public void testIsActivated() {
        /* Defaults should be activated */
        assertTrue(monitor.isActivated());
    }

    @Test
    public void testIsActivatedNotYetEndOfSupport() {
        /* End of support is next week, start of message is yesterday, should be activated */
        EndOfLifeAdminMonitor notActive = new EndOfLifeAdminMonitor(id, name, yesterday, nextWeek, url, dataFile, dataPattern);
        assertTrue(notActive.isActivated());
    }

    @Test
    public void testIsActivatedNotYetBeginDate() {
        /* End of support is next week, start of message is tomorrow, should not be activated */
        EndOfLifeAdminMonitor notActive = new EndOfLifeAdminMonitor(id, name, tomorrow, nextWeek, url, dataFile, dataPattern);
        assertFalse(notActive.isActivated());
    }

    @Test
    public void testIsActivatedPatternNotMatched() {
        /* Pattern does not match, should not be activated */
        Pattern nonMatching = Pattern.compile("xyzzy");
        EndOfLifeAdminMonitor notActive = new EndOfLifeAdminMonitor(id, name, threeDaysAgo, yesterday, url, dataFile, nonMatching);
        assertFalse(notActive.isActivated());
    }
}
