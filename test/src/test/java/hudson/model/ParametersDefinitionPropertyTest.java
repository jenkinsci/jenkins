/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import java.net.URL;
import java.util.Locale;
import java.util.logging.Level;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest2;

public class ParametersDefinitionPropertyTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logs = new LoggerRule();

    @Issue("JENKINS-31458")
    @Test
    public void customNewInstance() throws Exception {
        logs.record(Descriptor.class, Level.ALL);
        KrazyParameterDefinition kpd = new KrazyParameterDefinition("kpd", "desc", "KrAzY");
        FreeStyleProject p = r.createFreeStyleProject();
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(kpd);
        p.addProperty(pdp);
        r.configRoundtrip(p);
        pdp = p.getProperty(ParametersDefinitionProperty.class);
        kpd = (KrazyParameterDefinition) pdp.getParameterDefinition("kpd");
        assertEquals("desc", kpd.getDescription());
        assertEquals("krazy", kpd.field);
    }

    public static class KrazyParameterDefinition extends ParameterDefinition {

        public final String field;

        // not @DataBoundConstructor
        public KrazyParameterDefinition(String name, String description, String field) {
            super(name, description);
            this.field = field;
        }

        @Override
        public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParameterValue createValue(StaplerRequest2 req) {
            throw new UnsupportedOperationException();
        }

        @TestExtension("customNewInstance")
        public static class DescriptorImpl extends ParameterDescriptor {

            @Override
            public ParameterDefinition newInstance(StaplerRequest2 req, JSONObject formData) {
                return new KrazyParameterDefinition(formData.getString("name"), formData.getString("description"), formData.getString("field").toLowerCase(Locale.ENGLISH));
            }

        }

    }

    @Issue("JENKINS-66105")
    @Test
    public void statusCodes() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        FreeStyleProject p = r.createFreeStyleProject("p");
        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(new StringParameterDefinition("K"));
        p.addProperty(pdp);
        p.setConcurrentBuild(true);
        p.setAssignedLabel(Label.get("nonexistent")); // force it to stay in queue
        JenkinsRule.WebClient wc = r.createWebClient();
        wc.withBasicApiToken("dev");
        assertThat("initially 201 Created queue item", buildWithParameters(wc, "v1").getStatusCode(), is(201));
        WebResponse rsp = buildWithParameters(wc, "v1");
        assertThat("then 303 See Other → 200 OK", rsp.getStatusCode(), is(200));
        assertThat("offers advice on API", rsp.getContentAsString(), containsString("api/json?tree="));
        assertThat("201 Created queue item for different key", buildWithParameters(wc, "v2").getStatusCode(), is(201));
    }

    private WebResponse buildWithParameters(JenkinsRule.WebClient wc, String value) throws Exception {
        return wc.getPage(new WebRequest(new URL(wc.getContextPath() + "job/p/buildWithParameters?K=" + value), HttpMethod.POST)).getWebResponse();
    }

}
