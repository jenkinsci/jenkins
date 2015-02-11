/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package hudson.util;

import hudson.diagnosis.OldDataMonitor;
import hudson.model.FreeStyleProject;
import hudson.model.Saveable;
import java.util.Collections;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class RobustReflectionConverterTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-21024")
    @LocalData
    @Test public void randomExceptionsReported() throws Exception {
        FreeStyleProject p = r.jenkins.getItemByFullName("j", FreeStyleProject.class);
        assertNotNull(p);
        assertEquals(Collections.emptyMap(), p.getTriggers());
        OldDataMonitor odm = (OldDataMonitor) r.jenkins.getAdministrativeMonitor("OldData");
        Map<Saveable,OldDataMonitor.VersionRange> data = odm.getData();
        assertEquals(Collections.singleton(p), data.keySet());
        String text = data.values().iterator().next().extra;
        assertTrue(text, text.contains("Could not call hudson.triggers.TimerTrigger.readResolve"));
    }

}
