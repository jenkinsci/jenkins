/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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
package jenkins.telemetry;

import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import jenkins.telemetry.impl.java11.CatcherClassLoader;
import jenkins.telemetry.impl.java11.MissingClassTelemetry;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * This test needs to be here to be able to modify the {@link Telemetry#ENDPOINT} as it's package protected.
 */
public class MissingClassTelemetryTest {
    private static final String TELEMETRY_ENDPOINT = "uplink";
    private CatcherClassLoader cl;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static JSONObject received = null;

    @Before
    public void prepare() throws Exception {
        received = null;
        cl = new CatcherClassLoader(this.getClass().getClassLoader());
        Telemetry.ENDPOINT = j.getURL().toString() + TELEMETRY_ENDPOINT + "/events";
        j.jenkins.setNoUsageStatistics(false); // tests usually don't submit this, but we need this
    }

    /**
     * Test if the telemetry sent works and the received data is the expected for a specific case (5 occurrences of the
     * same stack trace).
     * @throws InterruptedException if the thread is interrupted while sleeping
     */
    @Test
    public void telemetrySentWorks() throws InterruptedException {
        Assume.assumeTrue("The telemetry should be enabled", MissingClassTelemetry.enabled());

        // Generate 5 events
        for(int i = 0; i < 5; i++) {
            try {
                cl.loadClass("sun.java.MyNonExistentClass");
            } catch (ClassNotFoundException ignored) {
            }
        }

        // Run the telemetry sent
        ExtensionList.lookupSingleton(Telemetry.TelemetryReporter.class).doRun();
        do {

            Thread.sleep(250);
        } while (received == null); // this might end up being flaky due to 1 to many active telemetry trials


        // The telemetry stuff sent is the class expected, the number of events is 1, the class not found is the
        // expected and the number of occurrences is the expected
        assertEquals(MissingClassTelemetry.class.getName(), received.getString("type"));
        JSONArray events = received.getJSONObject("payload").getJSONArray("classMissingEvents");
        assertEquals(1, events.size());
        assertEquals("sun.java.MyNonExistentClass", ((JSONObject) events.get(0)).get("className"));
        assertEquals(5, Integer.parseInt( (String) ((JSONObject) events.get(0)).get("occurrences")));
    }

    /**
     * Avoid crumb checking (CSRF)
     */
    @TestExtension
    public static class NoCrumb extends CrumbExclusion {
        @Override
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && pathInfo.startsWith("/uplink")) {
                chain.doFilter(request, response);
                return true;
            }
            return false;
        }
    }

    @TestExtension
    public static class TelemetryReceiver implements UnprotectedRootAction {
        public void doEvents(StaplerRequest request, StaplerResponse response) throws IOException {
            StringWriter sw = new StringWriter();
            IOUtils.copy(request.getInputStream(), sw, StandardCharsets.UTF_8);
            received = JSONObject.fromObject(sw.toString());
        }

        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getUrlName() {
            return TELEMETRY_ENDPOINT;
        }
    }
}
