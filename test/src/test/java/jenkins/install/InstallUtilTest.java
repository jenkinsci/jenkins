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

import hudson.Main;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.lang.reflect.Field;

/**
 * Test
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class InstallUtilTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    
    @Before
    public void setup() {
        // JenkinsRule will have created the last exec file (indirectly),
        // so remove it so we can fake the tests.
        InstallUtil.getLastExecVersionFile().delete();
        // Disable the unit test flag.
        Main.isUnitTest = false;
    }
    
    @After
    public void tearDown() {
        // Reset the unit test flag back to its default.
        Main.isUnitTest = true;
    }

    /**
     * Test jenkins startup sequences and the changes to the startup type..
     */
    @Test
    public void test_typeTransitions() {
        // A new test instance
        Assert.assertEquals(InstallState.NEW, InstallUtil.getInstallState());

        // Save the last exec version. This will only be done by Jenkins after one of:
        //   1. A successful run of the install wizard.
        //   2. A success upgrade.
        //   3. A successful restart.
        InstallUtil.saveLastExecVersion();

        // Fudge things a little now, pretending there's a restart...

        // Now if we ask what is the InstallState, we should be told it's a RESTART because
        // the install wizard is complete and the version matches the currently executing
        // Jenkins version.
        Assert.assertEquals(InstallState.RESTART, InstallUtil.getInstallState());

        // Fudge things again, changing the stored version to something old, faking an upgrade...
        InstallUtil.saveLastExecVersion("1.584");
        Assert.assertEquals(InstallState.UPGRADE, InstallUtil.getInstallState());

        // Fudge things yet again, changing the stored version to something very very new, faking a downgrade...
        InstallUtil.saveLastExecVersion("1000.0");
        Assert.assertEquals(InstallState.DOWNGRADE, InstallUtil.getInstallState());
    }
    

    /**
     * Test jenkins startup sequences and the changes to the startup type..
     */
    @Test
    public void test_getLastExecVersion() throws Exception {
        // Delete the config file, forcing getLastExecVersion to return
        // the default/unset version value.
        InstallUtil.getConfigFile().delete();        
        Assert.assertEquals("1.0", InstallUtil.getLastExecVersion());
        
        // Set the version to some stupid value and check again. This time,
        // getLastExecVersion should read it from the file.
        setStoredVersion("9.123");       
        Assert.assertEquals("9.123", InstallUtil.getLastExecVersion());
    }    

    private void setStoredVersion(String version) throws Exception {
        Field versionField = Jenkins.class.getDeclaredField("version");
        versionField.setAccessible(true);
        versionField.set(jenkinsRule.jenkins, version);
        Assert.assertEquals(version, Jenkins.getStoredVersion().toString());
        // Force a save of the config.xml
        jenkinsRule.jenkins.save();
    }
}
