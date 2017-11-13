/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import hudson.tasks.BuildTrigger;
import java.util.Collections;
import jenkins.model.Jenkins;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

// TODO merge with AbstractProjectTest when converted from Groovy to Java
public class AbstractProject2Test {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("SECURITY-617")
    @Test
    public void upstreamDownstreamExportApi() throws Exception {
        FreeStyleProject us = r.createFreeStyleProject("upstream-project");
        FreeStyleProject ds = r.createFreeStyleProject("downstream-project");
        us.getPublishersList().add(new BuildTrigger(Collections.singleton(ds), Result.SUCCESS));
        r.jenkins.rebuildDependencyGraph();
        assertEquals(Collections.singletonList(ds), us.getDownstreamProjects());
        assertEquals(Collections.singletonList(us), ds.getUpstreamProjects());
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.READ).everywhere().toEveryone().
            grant(Item.READ).everywhere().to("alice").
            grant(Item.READ).onItems(us).to("bob").
            grant(Item.READ).onItems(ds).to("charlie"));

        String api = r.createWebClient().withBasicCredentials("alice", "alice").goTo(us.getUrl() + "api/json?pretty", null).getWebResponse().getContentAsString();
        System.out.println(api);
        assertThat(api, containsString("downstream-project"));

        api = r.createWebClient().withBasicCredentials("alice", "alice").goTo(ds.getUrl() + "api/json?pretty", null).getWebResponse().getContentAsString();
        System.out.println(api);
        assertThat(api, containsString("upstream-project"));

        api = r.createWebClient().withBasicCredentials("bob", "bob").goTo(us.getUrl() + "api/json?pretty", null).getWebResponse().getContentAsString();
        System.out.println(api);
        assertThat(api, not(containsString("downstream-project")));

        api = r.createWebClient().withBasicCredentials("charlie", "charlie").goTo(ds.getUrl() + "api/json?pretty", null).getWebResponse().getContentAsString();
        System.out.println(api);
        assertThat(api, not(containsString("upstream-project")));
    }

}
