/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import hudson.cli.CLICommandInvoker;
import hudson.cli.CreateJobCommand;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class RunIdMigratorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void legacyIdsPresent() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        File legacyIds = new File(p.getBuildDir(), "legacyIds");
        assertTrue(legacyIds.exists());
    }

    @Issue("JENKINS-64356")
    @Test
    public void legacyIdsPresentViaRestApi() throws Exception {
        User user = User.getById("user", true);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.CREATE)
                .everywhere()
                .to(user.getId()));
        String jobName = "test" + j.jenkins.getItems().size();
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login(user.getId());
            WebRequest req = new WebRequest(wc.createCrumbedUrl("createItem?name=" + jobName), HttpMethod.POST);
            req.setAdditionalHeader("Content-Type", "application/xml");
            req.setRequestBody("<project/>");
            wc.getPage(req);
        }
        FreeStyleProject p = j.jenkins.getItemByFullName(jobName, FreeStyleProject.class);
        assertNotNull(p);
        File legacyIds = new File(p.getBuildDir(), "legacyIds");
        assertTrue(legacyIds.exists());
    }

    @Issue("JENKINS-64356")
    @Test
    public void legacyIdsPresentViaCli() {
        String jobName = "test" + j.jenkins.getItems().size();
        CLICommandInvoker invoker = new CLICommandInvoker(j, new CreateJobCommand());
        CLICommandInvoker.Result result = invoker.withStdin(
                        new ByteArrayInputStream("<project/>".getBytes(StandardCharsets.UTF_8)))
                .invokeWithArgs(jobName);
        assertThat(result, succeededSilently());
        FreeStyleProject p = j.jenkins.getItemByFullName(jobName, FreeStyleProject.class);
        assertNotNull(p);
        File legacyIds = new File(p.getBuildDir(), "legacyIds");
        assertTrue(legacyIds.exists());
    }
}
