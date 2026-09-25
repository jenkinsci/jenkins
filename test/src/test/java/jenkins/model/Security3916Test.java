/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import hudson.security.GlobalMatrixAuthorizationStrategy;
import java.net.URI;
import java.util.List;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

@Issue("SECURITY-3916")
@WithJenkins
class Security3916Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    public static class ArbitraryClass {
        public static volatile boolean instantiated = false;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public ArbitraryClass() {
            instantiated = true;
        }
    }

    @Test
    void arbitraryClassIsNotInstantiated() throws Exception {
        ArbitraryClass.instantiated = false;

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.READ, "manager");
        auth.add(Jenkins.MANAGE, "manager");
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);

        String json = "{\"jenkins-model-GlobalQuietPeriodConfiguration\":{\"quietPeriod\":\"5\"},"
                + "\"jenkins-model-GlobalSCMRetryCountConfiguration\":{\"scmCheckoutRetryCount\":0},"
                + "\"jenkins-model-GlobalProjectNamingStrategyConfiguration\":"
                + "{\"useProjectNamingStrategy\":{\"namingStrategy\":"
                + "{\"$class\":\"" + ArbitraryClass.class.getName() + "\"}}}}";

        Page page = submitGlobalConfig(json);

        assertThat(page.getWebResponse().getStatusCode(), is(400));
        assertThat("arbitrary class must not be instantiated", ArbitraryClass.instantiated, is(false));
        assertThat(j.jenkins.getProjectNamingStrategy(),
                instanceOf(ProjectNamingStrategy.DefaultProjectNamingStrategy.class));
        assertThat(page.getWebResponse().getContentAsString(),
                containsString(ArbitraryClass.class.getName() + " is not a " + ProjectNamingStrategy.class.getName()));
    }

    @Test
    void nonExistentClassIsRejected() throws Exception {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.READ, "manager");
        auth.add(Jenkins.MANAGE, "manager");
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);

        String json = "{\"jenkins-model-GlobalQuietPeriodConfiguration\":{\"quietPeriod\":\"5\"},"
                + "\"jenkins-model-GlobalSCMRetryCountConfiguration\":{\"scmCheckoutRetryCount\":0},"
                + "\"jenkins-model-GlobalProjectNamingStrategyConfiguration\":"
                + "{\"useProjectNamingStrategy\":{\"namingStrategy\":"
                + "{\"$class\":\"com.example.DoesNotExist\"}}}}";

        Page page = submitGlobalConfig(json);

        assertThat(page.getWebResponse().getStatusCode(), is(400));
        assertThat(j.jenkins.getProjectNamingStrategy(),
                instanceOf(ProjectNamingStrategy.DefaultProjectNamingStrategy.class));

        assertThat(page.getWebResponse().getContentAsString(),
                containsString("ClassNotFoundException: com.example.DoesNotExist"));
    }

    @Test
    void validNamingStrategyIsAccepted() throws Exception {
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.READ, "manager");
        auth.add(Jenkins.MANAGE, "manager");
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);

        String json = "{\"jenkins-model-GlobalQuietPeriodConfiguration\":{\"quietPeriod\":\"5\"},"
                + "\"jenkins-model-GlobalSCMRetryCountConfiguration\":{\"scmCheckoutRetryCount\":0},"
                + "\"jenkins-model-GlobalProjectNamingStrategyConfiguration\":"
                + "{\"useProjectNamingStrategy\":{\"namingStrategy\":"
                + "{\"$class\":\"jenkins.model.ProjectNamingStrategy$PatternProjectNamingStrategy\","
                + "\"namePattern\":\"foo.*\",\"description\":\"desc\",\"forceExistingJobs\":false}}}}";

        Page page = submitGlobalConfig(json);

        assertThat(page.getWebResponse().getStatusCode(), is(200));
        assertThat(j.jenkins.getProjectNamingStrategy(),
                instanceOf(ProjectNamingStrategy.PatternProjectNamingStrategy.class));
        ProjectNamingStrategy.PatternProjectNamingStrategy strategy =
                (ProjectNamingStrategy.PatternProjectNamingStrategy) j.jenkins.getProjectNamingStrategy();
        assertThat(strategy.getNamePattern(), is("foo.*"));
    }

    private Page submitGlobalConfig(String json) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        wc.login("manager");

        WebRequest req = new WebRequest(
                new URI(wc.getContextPath() + "configSubmit").toURL(), HttpMethod.POST);
        req.setRequestParameters(List.of(new NameValuePair("json", json)));
        return wc.getPage(req);
    }
}
