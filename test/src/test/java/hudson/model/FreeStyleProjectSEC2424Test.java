/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

//TODO merge back to FreeStyleProjectTest after security release
public class FreeStyleProjectSEC2424Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateJobWithTrailingDot_withoutOtherJob() throws Exception {
        assertThat(j.jenkins.getItems(), hasSize(0));
        try {
            j.jenkins.createProjectFromXML("jobA.", new ByteArrayInputStream("<project/>".getBytes()));
            fail("Adding the job should have thrown an exception during checkGoodName");
        }
        catch (Failure e) {
            assertEquals(Messages.Hudson_TrailingDot(), e.getMessage());
        }
        assertThat(j.jenkins.getItems(), hasSize(0));
    }

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateJobWithTrailingDot_withExistingJob() throws Exception {
        assertThat(j.jenkins.getItems(), hasSize(0));
        j.createFreeStyleProject("jobA");
        assertThat(j.jenkins.getItems(), hasSize(1));
        try {
            j.jenkins.createProjectFromXML("jobA.", new ByteArrayInputStream("<project/>".getBytes()));
            fail("Adding the job should have thrown an exception during checkGoodName");
        }
        catch (Failure e) {
            assertEquals(Messages.Hudson_TrailingDot(), e.getMessage());
        }
        assertThat(j.jenkins.getItems(), hasSize(1));
    }

    @Issue("SECURITY-2424")
    @Test public void cannotCreateJobWithTrailingDot_exceptIfEscapeHatchIsSet() throws Exception {
        String propName = Jenkins.NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP;
        String initialValue = System.getProperty(propName);
        System.setProperty(propName, "false");
        try {
            assertThat(j.jenkins.getItems(), hasSize(0));
            j.jenkins.createProjectFromXML("jobA.", new ByteArrayInputStream("<project/>".getBytes()));
        }
        finally {
            if (initialValue == null) {
                System.clearProperty(propName);
            } else {
                System.setProperty(propName, initialValue);
            }
        }
        assertThat(j.jenkins.getItems(), hasSize(1));
    }
}
