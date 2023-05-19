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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;

public class EndOfLifeOperatingSystemAdminMonitorTest {

    private final EndOfLifeOperatingSystemAdminMonitor monitor;

    public EndOfLifeOperatingSystemAdminMonitorTest() throws IOException {
        this.monitor = new EndOfLifeOperatingSystemAdminMonitor();
    }

    @Test
    public void testGetDisplayName() {
        assertThat(monitor.getDisplayName(), is(monitor.getClass().getName()));
    }

    @Test
    public void testGetUrl() {
        assertThat(monitor.getUrl(), is("administrativeMonitor/" + monitor.getClass().getName()));
    }

    @Test
    public void testGetSearchUrl() {
        assertThat(monitor.getSearchUrl(), is("administrativeMonitor/" + monitor.getClass().getName()));
    }

    @Test
    public void testGetOperatingSystemList() throws IOException {
        assertNotNull(new EndOfLifeOperatingSystemAdminMonitor());
    }

    @Test
    public void testIsActivated() throws IOException {
        assertTrue(monitor.isActivated());
    }

    @Test
    public void testNotIsActivatedWhenDisabled() throws IOException {
        monitor.setDisabled(true);
        assertFalse(monitor.isActivated());
    }

    @Test
    public void testIsSecurity() throws IOException {
        assertFalse(monitor.isSecurity());
    }

}
