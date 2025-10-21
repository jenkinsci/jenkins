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

package jenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@WithJenkins
class I18nTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    @Test
    void test_baseName_unspecified() throws IOException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle").getJSONObject();
        assertEquals("error", response.getString("status"));
        assertEquals("Mandatory parameter 'baseName' not specified.", response.getString("message"));
    }

    @Test
    void test_baseName_unknown() throws IOException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=com.acme.XyzWhatever").getJSONObject();
        assertEquals("error", response.getString("status"));
        assertThat(response.getString("message"), startsWith("Can't find bundle for base name com.acme.XyzWhatever"));
    }

    @Issue("JENKINS-35270")
    @Test
    void test_baseName_plugin() throws Exception {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=org.jenkinsci.plugins.matrixauth.Messages").getJSONObject();
        assertEquals("ok", response.getString("status"), response.toString());
        JSONObject data = response.getJSONObject("data");
        assertEquals("Matrix-based security", data.getString("GlobalMatrixAuthorizationStrategy.DisplayName"));
    }

    @Test
    void test_valid() throws IOException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=hudson.logging.Messages&language=de").getJSONObject();
        assertEquals("ok", response.getString("status"));
        JSONObject data = response.getJSONObject("data");
        assertEquals("Initialisiere Log-Rekorder", data.getString("LogRecorderManager.init"));
    }

    // variant testing
    @Issue("JENKINS-39034")
    @Test
    void test_valid_region_variant() throws IOException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=jenkins.i18n.Messages&language=en_AU_variant").getJSONObject();
        assertEquals("ok", response.getString("status"));
        JSONObject data = response.getJSONObject("data");
        assertEquals("value_au_variant", data.getString("Key"));
    }

    //country testing with delimiter '-' instead of '_'
    @Issue("JENKINS-39034")
    @Test
    void test_valid_region() throws IOException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=jenkins.i18n.Messages&language=en-AU").getJSONObject();
        assertEquals("ok", response.getString("status"));
        JSONObject data = response.getJSONObject("data");
        assertEquals("value_au", data.getString("Key"));
    }

    //fallthrough to default language if variant does not exit
    @Issue("JENKINS-39034")
    @Test
    void test_valid_fallback() throws IOException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=jenkins.i18n.Messages&language=en_NZ_variant").getJSONObject();
        assertEquals("ok", response.getString("status"));
        JSONObject data = response.getJSONObject("data");
        assertEquals("value", data.getString("Key"));
    }

    // testing with unknown language falls through to default language
    @Test
    void test_unsupported_language() throws IOException {
        JSONObject response = jenkinsRule.getJSON("i18n/resourceBundle?baseName=jenkins.i18n.Messages&language=xyz").getJSONObject();
        assertEquals("ok", response.getString("status"));
        JSONObject data = response.getJSONObject("data");
        assertEquals("value", data.getString("Key"));
    }

}
