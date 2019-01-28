/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import static org.junit.Assert.*;

import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

@For(View.AsynchPeople.class)
public class AsynchPeopleTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Issue("JENKINS-18641")
    @Test public void display() throws Exception {
        User.getById("bob", true);
        JenkinsRule.WebClient wc = j.createWebClient();

        HtmlPage page = wc.goTo("asynchPeople");
        assertEquals(0, wc.waitForBackgroundJavaScript(120000));
        boolean found = false;
        for (DomElement div : page.getElementsByTagName("div")) {
            if (div.getAttribute("class").contains("app-progress-bar")) {
                found = true;
                assertEquals("display: none;", div.getAttribute("style"));
                break;
            }
        }
        assertTrue(found);
        /* TODO this still fails occasionally, for reasons TBD (I think because User.getAll sometimes is empty):
        assertNotNull(page.getElementById("person-bob"));
        */
    }

    @Issue("JENKINS-18884")
    @Test
    @LocalData
    public void testProjectPermission() throws Exception {
        User user1 = User.get("user1", false, Collections.emptyMap());
        User admin = User.get("admin", false, Collections.emptyMap());

        // p1 can be accessed by user1, admin
        // p2 can be accessed by admin
        FreeStyleProject p1 = j.createFreeStyleProject();
        {
            Map<Permission, Set<String>> permissions = new HashMap<>();
            permissions.put(Item.READ, Sets.newHashSet(user1.getId(), admin.getId()));
            p1.addProperty(new AuthorizationMatrixProperty(permissions));
        }
        assertFalse(p1.getACL().hasPermission(Jenkins.ANONYMOUS, Item.READ));
        assertTrue(p1.getACL().hasPermission(user1.impersonate(), Item.READ));
        assertTrue(p1.getACL().hasPermission(admin.impersonate(), Item.READ));

        FreeStyleProject p2 = j.createFreeStyleProject();
        {
            Map<Permission, Set<String>> permissions = new HashMap<>();
            permissions.put(Item.READ, Sets.newHashSet(admin.getId()));
            p1.addProperty(new AuthorizationMatrixProperty(permissions));
        }
        assertFalse(p2.getACL().hasPermission(Jenkins.ANONYMOUS, Item.READ));
        assertFalse(p2.getACL().hasPermission(user1.impersonate(), Item.READ));
        assertTrue(p2.getACL().hasPermission(admin.impersonate(), Item.READ));

        // create fake changelog
        {
            FakeChangeLogSCM scm = new FakeChangeLogSCM();
            scm.addChange().withAuthor("author1");
            scm.addChange().withAuthor("author2");
            p1.setScm(scm);
        }
        {
            FakeChangeLogSCM scm = new FakeChangeLogSCM();
            scm.addChange().withAuthor("author3");
            scm.addChange().withAuthor("author4");
            p2.setScm(scm);
        }

        j.assertBuildStatusSuccess(p1.scheduleBuild2(0));
        j.assertBuildStatusSuccess(p2.scheduleBuild2(0));

        ListView view = new ListView("test", j.jenkins);
        view.add(p1);
        view.add(p2);

        {
            SecurityContextHolder.getContext().setAuthentication(Jenkins.ANONYMOUS);
            View.AsynchPeople people = view.getAsynchPeople();
            people.start();
            while(!people.isFinished()) {
                Thread.sleep(100);
            }
            Collection<String> authors = Collections2.transform(people.getModified(), input -> input.getId());
            assertFalse(authors.contains("author1"));
            assertFalse(authors.contains("author2"));
            assertFalse(authors.contains("author3"));
            assertFalse(authors.contains("author4"));
        }

        {
            SecurityContextHolder.getContext().setAuthentication(user1.impersonate());
            View.AsynchPeople people = view.getAsynchPeople();
            people.start();
            while(!people.isFinished()) {
                Thread.sleep(100);
            }
            Collection<String> authors = Collections2.transform(people.getModified(), input -> input.getId());
            assertTrue(authors.contains("author1"));
            assertTrue(authors.contains("author2"));
            assertFalse(authors.contains("author3"));
            assertFalse(authors.contains("author4"));
        }

        {
            SecurityContextHolder.getContext().setAuthentication(admin.impersonate());
            View.AsynchPeople people = view.getAsynchPeople();
            people.start();
            while(!people.isFinished()) {
                Thread.sleep(100);
            }
            Collection<String> authors = Collections2.transform(people.getModified(), input -> input.getId());
            assertTrue(authors.contains("author1"));
            assertTrue(authors.contains("author2"));
            assertTrue(authors.contains("author3"));
            assertTrue(authors.contains("author4"));
        }

    }

}
