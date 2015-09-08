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

import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.lang.reflect.Field;

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

    /**
     * Test jenkins startup upgrade sequence, making sure we can determine the kind of upgrade to perform.
     *
     * @see jenkins.install.StartupUtil#hasStartedSinceUnbundlingEpoc()
     */
    @Test
    public void test_hasStartedSinceUnbundlingEpoc() throws Exception {
        // Fake the stored version. Doesn't really matter what the value is,
        // so long as it's not "1.0" ala the Jenkins default.
        setStoredVersion("1.609");

        // Should be flagged as an upgrade
        Assert.assertEquals(StartupType.UPGRADE, StartupUtil.getStartupType());

        // But we should be able to decide what kind of upgrade to perform based
        // on StartupUtil.hasStartedSinceUnbundlingEpoc() (see Javadoc).
        Assert.assertFalse(StartupUtil.hasStartedSinceUnbundlingEpoc());

        // Saving a last version file indicates that the Jenkins instance has run a post epoc released version
        // (because creation of that file only started then).
        StartupUtil.saveLastExecVersion();

        // Now it should look as though the last running version was a release from after the epoc, which means
        // different kind of upgrade.
        Assert.assertTrue(StartupUtil.hasStartedSinceUnbundlingEpoc());
    }

    private void setStoredVersion(String version) throws Exception {
        Field versionField = Jenkins.class.getDeclaredField("version");
        versionField.setAccessible(true);
        versionField.set(jenkinsRule.jenkins, version);
        Assert.assertEquals(version, Jenkins.getStoredVersion().toString());
    }
}
