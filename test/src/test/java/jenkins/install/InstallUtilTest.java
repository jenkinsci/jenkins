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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.Main;
import hudson.model.UpdateCenter;
import hudson.model.UpdateCenter.DownloadJob.Failure;
import hudson.model.UpdateCenter.DownloadJob.InstallationStatus;
import hudson.model.UpdateCenter.DownloadJob.Installing;
import hudson.model.UpdateCenter.DownloadJob.Pending;
import hudson.model.UpdateCenter.DownloadJob.Success;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.model.UpdateSite;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;

/**
 * Test
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Tag("SmokeTest")
@WithJenkins
class InstallUtilTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    @BeforeEach
    void setup() {
        // JenkinsRule will have created the last exec file (indirectly),
        // so remove it so we can fake the tests.
        InstallUtil.getLastExecVersionFile().delete();
        // Disable the unit test flag.
        Main.isUnitTest = false;
    }

    @AfterEach
    void tearDown() {
        // Reset the unit test flag back to its default.
        Main.isUnitTest = true;
    }

    /**
     * Test jenkins startup sequences and the changes to the startup type..
     */
    @Test
    void test_typeTransitions() throws IOException, ServletException {
        InstallUtil.getLastExecVersionFile().delete();
        InstallUtil.getConfigFile().delete();

        // A new test instance sets up security first
        assertEquals(InstallState.INITIAL_SECURITY_SETUP, InstallUtil.getNextInstallState(InstallState.UNKNOWN));

        // And proceeds to the new state
        assertEquals(InstallState.NEW, InstallUtil.getNextInstallState(InstallState.INITIAL_SECURITY_SETUP));

        // Save the last exec version. This will only be done by Jenkins after one of:
        //   1. A successful run of the install wizard.
        //   2. A success upgrade.
        //   3. A successful restart.
        Jenkins.get().getSetupWizard().completeSetup();

        // Fudge things a little now, pretending there's a restart...

        // Now if we ask what is the InstallState, we should be told it's a RESTART because
        // the install wizard is complete and the version matches the currently executing
        // Jenkins version.
        assertEquals(InstallState.RESTART, InstallUtil.getNextInstallState(InstallState.UNKNOWN));

        // Fudge things again, changing the stored version to something old, faking an upgrade...
        InstallUtil.saveLastExecVersion("1.584");
        assertEquals(InstallState.UPGRADE, InstallUtil.getNextInstallState(InstallState.UNKNOWN));

        // Fudge things yet again, changing the stored version to something very very new, faking a downgrade...
        InstallUtil.saveLastExecVersion("1000000.0");
        assertEquals(InstallState.DOWNGRADE, InstallUtil.getNextInstallState(InstallState.UNKNOWN));
    }


    /**
     * Test jenkins startup sequences and the changes to the startup type..
     */
    @Test
    void test_getLastExecVersion() throws Exception {
        Main.isUnitTest = true;

        // Delete the config file, forcing getLastExecVersion to return
        // the default/unset version value.
        InstallUtil.getConfigFile().delete();
        assertEquals("1.0", InstallUtil.getLastExecVersion());

        // Set the version to some stupid value and check again. This time,
        // getLastExecVersion should read it from the file.
        setStoredVersion("9.123");
        assertEquals("9.123", InstallUtil.getLastExecVersion());
    }

    private void setStoredVersion(String version) throws Exception {
        Jenkins.VERSION = version;
        // Force a save of the config.xml
        jenkinsRule.jenkins.save();
        assertEquals(version, Jenkins.getStoredVersion().toString());
    }

    /**
     * Validate proper statuses are persisted and install status is cleared when invoking appropriate methods on {@link InstallUtil}
     */
    @Test
    void testSaveAndRestoreInstallingPlugins() {
        final List<UpdateCenterJob> updates = new ArrayList<>();

        final Map<String, String> nameMap = new HashMap<>();

        new UpdateCenter() { // inner classes...
            {
                new UpdateSite("foo", "http://omg.org") {
                    {
                        for (String name : Arrays.asList("pending-plug:Pending", "installing-plug:Installing", "failure-plug:Failure", "success-plug:Success")) {
                            String statusType = name.split(":")[1];
                            name = name.split(":")[0];

                            InstallationStatus status = switch (statusType) {
                                case "Success" -> Mockito.mock(Success.class, Mockito.CALLS_REAL_METHODS);
                                case "Failure" -> Mockito.mock(Failure.class, Mockito.CALLS_REAL_METHODS);
                                case "Installing" -> Mockito.mock(Installing.class, Mockito.CALLS_REAL_METHODS);
                                case null, default -> Mockito.mock(Pending.class, Mockito.CALLS_REAL_METHODS);
                            };

                            nameMap.put(statusType, status.getClass().getSimpleName());

                            JSONObject json = new JSONObject();
                            json.put("name", name);
                            json.put("version", "1.1");
                            json.put("url", "http://google.com");
                            json.put("dependencies", new JSONArray());
                            Plugin p = new Plugin(getId(), json);

                            InstallationJob job = new InstallationJob(p, null, (Authentication) null, false);
                            job.status = status;
                            job.setCorrelationId(UUID.randomUUID()); // this indicates the plugin was 'directly selected'
                            updates.add(job);
                        }
                    }
                };
            }
        };

        InstallUtil.persistInstallStatus(updates);

        Map<String, String> persisted = InstallUtil.getPersistedInstallStatus();

        assertEquals(nameMap.get("Pending"), persisted.get("pending-plug"));
        assertEquals("Pending", persisted.get("installing-plug")); // only marked as success/fail after successful install
        assertEquals(nameMap.get("Failure"), persisted.get("failure-plug"));
        assertEquals(nameMap.get("Success"), persisted.get("success-plug"));

        InstallUtil.clearInstallStatus();

        persisted = InstallUtil.getPersistedInstallStatus();

        assertNull(persisted); // should be deleted
    }
}
