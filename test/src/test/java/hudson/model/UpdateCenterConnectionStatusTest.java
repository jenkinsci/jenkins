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

package hudson.model;

import static hudson.model.UpdateCenter.ConnectionStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.UnknownHostException;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@WithJenkins
class UpdateCenterConnectionStatusTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    @Test
    void doConnectionStatus_default_site() throws IOException {
        JSONObject response = jenkinsRule.getJSON("updateCenter/connectionStatus").getJSONObject();

        assertEquals("ok", response.getString("status"));
        JSONObject statusObj = response.getJSONObject("data");
        assertTrue(statusObj.has("updatesite"));
        assertTrue(statusObj.has("internet"));

        // The following is equivalent to the above
        response = jenkinsRule.getJSON("updateCenter/connectionStatus?siteId=default").getJSONObject();

        assertEquals("ok", response.getString("status"));
        statusObj = response.getJSONObject("data");
        assertTrue(statusObj.has("updatesite"));
        assertTrue(statusObj.has("internet"));
    }

    @Test
    void doConnectionStatus_unknown_site() throws IOException {
        JSONObject response = jenkinsRule.getJSON("updateCenter/connectionStatus?siteId=blahblah").getJSONObject();

        assertEquals("error", response.getString("status"));
        assertEquals("Cannot check connection status of the update site with ID='blahblah'. This update center cannot be resolved", response.getString("message"));
    }

    private UpdateSite updateSite = new UpdateSite(UpdateCenter.ID_DEFAULT, "http://xyz") {
        @Override
        public String getConnectionCheckUrl() {
            return "http://xyz./";
        }
    };

    @Test
    void test_states_allok() {
        UpdateCenter updateCenter = new UpdateCenter(new TestConfig());
        UpdateCenter.ConnectionCheckJob job = updateCenter.newConnectionCheckJob(updateSite);

        assertEquals(ConnectionStatus.PRECHECK, job.connectionStates.get(ConnectionStatus.INTERNET));
        assertEquals(ConnectionStatus.PRECHECK, job.connectionStates.get(ConnectionStatus.UPDATE_SITE));

        job.run();

        assertEquals(ConnectionStatus.OK, job.connectionStates.get(ConnectionStatus.INTERNET));
        assertEquals(ConnectionStatus.OK, job.connectionStates.get(ConnectionStatus.UPDATE_SITE));
    }

    @Test
    void test_states_internet_failed() {
        UpdateCenter updateCenter = new UpdateCenter(new TestConfig().failInternet());
        UpdateCenter.ConnectionCheckJob job = updateCenter.newConnectionCheckJob(updateSite);

        job.run();

        assertEquals(ConnectionStatus.FAILED, job.connectionStates.get(ConnectionStatus.INTERNET));
        assertEquals(ConnectionStatus.OK, job.connectionStates.get(ConnectionStatus.UPDATE_SITE));
    }

    @Test
    void test_states_uc_failed_timeout() {
        UpdateCenter updateCenter = new UpdateCenter(new TestConfig().failUCConnect());
        UpdateCenter.ConnectionCheckJob job = updateCenter.newConnectionCheckJob(updateSite);

        job.run();

        assertEquals(ConnectionStatus.OK, job.connectionStates.get(ConnectionStatus.INTERNET));
        assertEquals(ConnectionStatus.FAILED, job.connectionStates.get(ConnectionStatus.UPDATE_SITE));
    }

    @Test
    void test_states_uc_failed_UnknownHost() {
        UpdateCenter updateCenter = new UpdateCenter(new TestConfig().failUCResolve());
        UpdateCenter.ConnectionCheckJob job = updateCenter.newConnectionCheckJob(updateSite);

        job.run();

        assertEquals(ConnectionStatus.OK, job.connectionStates.get(ConnectionStatus.INTERNET));
        assertEquals(ConnectionStatus.FAILED, job.connectionStates.get(ConnectionStatus.UPDATE_SITE));
    }

    private static class TestConfig extends UpdateCenter.UpdateCenterConfiguration {

        private IOException checkConnectionException;
        private IOException checkUpdateCenterException;

        private TestConfig failInternet() {
            checkConnectionException = new IOException("Connection timed out");
            return this;
        }

        private TestConfig failUCResolve() {
            checkUpdateCenterException = new UnknownHostException("Unable to resolve UpdateCenter host address.");
            return this;
        }

        private TestConfig failUCConnect() {
            checkUpdateCenterException = new IOException("Connection timed out");
            return this;
        }

        @Override
        public void checkConnection(UpdateCenter.ConnectionCheckJob job, String connectionCheckUrl) throws IOException {
            if (checkConnectionException != null) {
                throw checkConnectionException;
            }
        }

        @Override
        public void checkUpdateCenter(UpdateCenter.ConnectionCheckJob job, String updateCenterUrl) throws IOException {
            if (checkUpdateCenterException != null) {
                throw checkUpdateCenterException;
            }
        }
    }
}
