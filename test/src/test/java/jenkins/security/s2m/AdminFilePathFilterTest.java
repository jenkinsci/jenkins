/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package jenkins.security.s2m;

import java.io.File;
import javax.inject.Inject;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class AdminFilePathFilterTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Inject
    AdminWhitelistRule rule;

    @Before
    public void setUp() {
        r.jenkins.getInjector().injectMembers(this);
        rule.setMasterKillSwitch(false);
    }

    @Issue({"JENKINS-27055", "SECURITY-358"})
    @Test
    public void matchBuildDir() throws Exception {
        File buildDir = r.buildAndAssertSuccess(r.createFreeStyleProject()).getRootDir();
        assertTrue(rule.checkFileAccess("write", new File(buildDir, "whatever")));
        assertFalse(rule.checkFileAccess("write", new File(buildDir, "build.xml")));
        // WorkflowRun:
        assertFalse(rule.checkFileAccess("write", new File(buildDir, "program.dat")));
        assertFalse(rule.checkFileAccess("write", new File(buildDir, "workflow/23.xml")));
    }

}
