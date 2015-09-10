/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package jenkins.install;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Test
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class StartupUtilTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    /**
     * Test jenkins startup sequence for a new install.
     */
    @Test
    public void test_newJenkinsInstall() {
        // A new test instance
        Assert.assertEquals(StartupType.NEW, StartupUtil.getStartupType());

        // Save the last exec version. This will only be done by Jenkins after one of:
        //   1. A successful run of the install wizard.
        //   2. A success upgrade.
        //   3. A successful restart.
        StartupUtil.saveLastExecVersion();

        // Fudge things a little now, pretending there's a restart...

        // Now if we ask what is the StartupType, we should be told it's a RESTART because
        // the install wizard is complete and the version matches the currently executing
        // Jenkins version.
        Assert.assertEquals(StartupType.RESTART, StartupUtil.getStartupType());

        // Fudge things again, changing the stored version to something old, faking an upgrade...
        StartupUtil.saveLastExecVersion("1.584");
        Assert.assertEquals(StartupType.UPGRADE, StartupUtil.getStartupType());
    }
}
