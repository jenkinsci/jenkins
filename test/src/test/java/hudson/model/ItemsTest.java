/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hudson.AbortException;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.cli.CopyJobCommand;
import hudson.cli.CreateJobCommand;
import hudson.security.ACL;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class ItemsTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public TemporaryFolder tmpRule = new TemporaryFolder();

    @Test public void getAllItems() throws Exception {
        MockFolder d = r.createFolder("d");
        MockFolder sub2 = d.createProject(MockFolder.class, "sub2");
        MockFolder sub2a = sub2.createProject(MockFolder.class, "a");
        MockFolder sub2c = sub2.createProject(MockFolder.class, "c");
        MockFolder sub2b = sub2.createProject(MockFolder.class, "b");
        MockFolder sub1 = d.createProject(MockFolder.class, "sub1");
        FreeStyleProject root = r.createFreeStyleProject("root");
        FreeStyleProject dp = d.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub1q = sub1.createProject(FreeStyleProject.class, "q");
        FreeStyleProject sub1p = sub1.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2ap = sub2a.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2bp = sub2b.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2cp = sub2c.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2alpha = sub2.createProject(FreeStyleProject.class, "alpha");
        FreeStyleProject sub2BRAVO = sub2.createProject(FreeStyleProject.class, "BRAVO");
        FreeStyleProject sub2charlie = sub2.createProject(FreeStyleProject.class, "charlie");
        assertEquals(Arrays.asList(dp, sub1p, sub1q, sub2ap, sub2alpha, sub2bp, sub2BRAVO, sub2cp, sub2charlie), d.getAllItems(FreeStyleProject.class));
        assertEquals(Arrays.<Item>asList(sub2a, sub2ap, sub2alpha, sub2b, sub2bp, sub2BRAVO, sub2c, sub2cp, sub2charlie), sub2.getAllItems(Item.class));
    }

    @Test public void getAllItemsPredicate() throws Exception {
        MockFolder d = r.createFolder("d");
        MockFolder sub2 = d.createProject(MockFolder.class, "sub2");
        MockFolder sub2a = sub2.createProject(MockFolder.class, "a");
        MockFolder sub2c = sub2.createProject(MockFolder.class, "c");
        MockFolder sub2b = sub2.createProject(MockFolder.class, "b");
        MockFolder sub1 = d.createProject(MockFolder.class, "sub1");
        FreeStyleProject root = r.createFreeStyleProject("root");
        FreeStyleProject dp = d.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub1q = sub1.createProject(FreeStyleProject.class, "q");
        FreeStyleProject sub1p = sub1.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2ap = sub2a.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2bp = sub2b.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2cp = sub2c.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2alpha = sub2.createProject(FreeStyleProject.class, "alpha");
        FreeStyleProject sub2BRAVO = sub2.createProject(FreeStyleProject.class, "BRAVO");
        FreeStyleProject sub2charlie = sub2.createProject(FreeStyleProject.class, "charlie");
        assertEquals(Arrays.asList(dp, sub1p, sub2ap, sub2bp, sub2cp), d.getAllItems(FreeStyleProject.class, t -> t.getName().equals("p")));
        assertEquals(Arrays.<Item>asList(sub2a, sub2alpha), sub2.getAllItems(Item.class, t -> t.getName().startsWith("a")));
    }

    @Issue("JENKINS-40252")
    @Test
    public void allItems() throws Exception {
        MockFolder d = r.createFolder("d");
        MockFolder sub2 = d.createProject(MockFolder.class, "sub2");
        MockFolder sub2a = sub2.createProject(MockFolder.class, "a");
        MockFolder sub2c = sub2.createProject(MockFolder.class, "c");
        MockFolder sub2b = sub2.createProject(MockFolder.class, "b");
        MockFolder sub1 = d.createProject(MockFolder.class, "sub1");
        FreeStyleProject root = r.createFreeStyleProject("root");
        FreeStyleProject dp = d.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub1q = sub1.createProject(FreeStyleProject.class, "q");
        FreeStyleProject sub1p = sub1.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2ap = sub2a.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2bp = sub2b.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2cp = sub2c.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2alpha = sub2.createProject(FreeStyleProject.class, "alpha");
        FreeStyleProject sub2BRAVO = sub2.createProject(FreeStyleProject.class, "BRAVO");
        FreeStyleProject sub2charlie = sub2.createProject(FreeStyleProject.class, "charlie");
        assertThat(d.allItems(FreeStyleProject.class), containsInAnyOrder(dp, sub1p, sub1q, sub2ap, sub2alpha,
                sub2bp, sub2BRAVO, sub2cp, sub2charlie));
        assertThat(sub2.allItems(Item.class), containsInAnyOrder((Item) sub2a, sub2ap, sub2alpha, sub2b, sub2bp,
                sub2BRAVO, sub2c, sub2cp, sub2charlie));
    }

    @Test public void allItemsPredicate() throws Exception {
        MockFolder d = r.createFolder("d");
        MockFolder sub2 = d.createProject(MockFolder.class, "sub2");
        MockFolder sub2a = sub2.createProject(MockFolder.class, "a");
        MockFolder sub2c = sub2.createProject(MockFolder.class, "c");
        MockFolder sub2b = sub2.createProject(MockFolder.class, "b");
        MockFolder sub1 = d.createProject(MockFolder.class, "sub1");
        FreeStyleProject root = r.createFreeStyleProject("root");
        FreeStyleProject dp = d.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub1q = sub1.createProject(FreeStyleProject.class, "q");
        FreeStyleProject sub1p = sub1.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2ap = sub2a.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2bp = sub2b.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2cp = sub2c.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2alpha = sub2.createProject(FreeStyleProject.class, "alpha");
        FreeStyleProject sub2BRAVO = sub2.createProject(FreeStyleProject.class, "BRAVO");
        FreeStyleProject sub2charlie = sub2.createProject(FreeStyleProject.class, "charlie");
        assertThat(d.allItems(FreeStyleProject.class, t -> t.getName().equals("p")), containsInAnyOrder(dp, sub1p, sub2ap, sub2bp, sub2cp));
        assertThat(sub2.allItems(Item.class, t -> t.getName().startsWith("a")), containsInAnyOrder(sub2a, sub2alpha));
    }

    @Issue("JENKINS-24825")
    @Test public void moveItem() throws Exception {
        File tmp = tmpRule.getRoot();
        r.jenkins.setRawBuildsDir(tmp.getAbsolutePath() + "/${ITEM_FULL_NAME}");
        MockFolder foo = r.createFolder("foo");
        MockFolder bar = r.createFolder("bar");
        FreeStyleProject test = foo.createProject(FreeStyleProject.class, "test");
        r.buildAndAssertSuccess(test);
        Items.move(test, bar);
        assertFalse(new File(tmp, "foo/test/1").exists());
        assertTrue(new File(tmp, "bar/test/1").exists());
    }

    // TODO would be more efficient to run these all as a single test case, but after a few Jetty seems to stop serving new content and new requests just hang.

    private void overwriteTargetSetUp() throws Exception {
        User.getById("attacker", true);

        // A fully visible item:
        r.createFreeStyleProject("visible").setDescription("visible");
        // An item known to exist but not visible:
        r.createFreeStyleProject("known").setDescription("known");
        // An item not even known to exist:
        r.createFreeStyleProject("secret").setDescription("secret");
        // A folder from which to launch move attacks:
        r.createFolder("d");
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.READ).everywhere().to("attacker").
            grant(Item.READ, Item.CONFIGURE, Item.CREATE, Item.DELETE).onPaths("(?!known|secret).*").to("attacker").
            grant(Item.DISCOVER).onPaths("known").to("attacker"));
    }

    private void createPrimaryView() throws Exception {
        // Create a view that only displays jobs that start with 'a-'
        ListView aView = new ListView("a-view");
        aView.setIncludeRegex("a-.*");
        r.jenkins.addView(aView);
        assertThat(aView.getItems(), is(empty()));
        assertFalse(aView.isDefault()); // Not yet the primary view

        // Create a view that only displays jobs that start with 'b-'
        ListView bView = new ListView("b-view");
        bView.setIncludeRegex("b-.*");
        r.jenkins.addView(bView);
        assertThat(bView.getItems(), is(empty()));
        assertFalse(bView.isDefault()); // Not the primary view

        // Make the a-view the primary view
        r.jenkins.setPrimaryView(aView);
        assertTrue(aView.isDefault()); // Now a-view is the primary view
    }

    /* JENKINS-74795 notes that new items created through the REST API
     * are made visible in the default view with 2.475-2.483.
     * They were not made visible in the default view with 2.474 and
     * earlier.
     */
    private void assertPrimaryViewEmpty() throws Exception {
        // Confirm no job is visible in primary view
        View view = r.jenkins.getPrimaryView();
        assertTrue(view.isDefault());
        assertThat(view.getItems(), is(empty()));
    }

    /** Control cases: if there is no such item yet, nothing is stopping you. */
    @Test public void overwriteNonexistentTarget() throws Exception {
        overwriteTargetSetUp();
        createPrimaryView();
        for (OverwriteTactic tactic : OverwriteTactic.values()) {
            tactic.run(r, "nonexistent");
            assertPrimaryViewEmpty();
            r.jenkins.getItem("nonexistent").delete();
        }
    }

    private void cannotOverwrite(String target) throws Exception {
        overwriteTargetSetUp();
        for (OverwriteTactic tactic : OverwriteTactic.values()) {
            assertThrows(tactic + " was not supposed to work against " + target, Exception.class, () -> tactic.run(r, target));
            assertEquals(tactic + " still overwrote " + target, target, r.jenkins.getItemByFullName(target, FreeStyleProject.class).getDescription());
        }
    }

    /** More control cases: for non-security-sensitive scenarios, we prevent you from overwriting existing items. */
    @Test public void overwriteVisibleTarget() throws Exception {
        cannotOverwrite("visible");
    }

    /** You may not overwrite an item you know is there even if you cannot see it. */
    @Test public void overwriteKnownTarget() throws Exception {
        cannotOverwrite("known");
    }

    /** You are somehow prevented from overwriting an item even if you did not previously know it was there. */
    @Issue("SECURITY-321")
    @Test public void overwriteHiddenTarget() throws Exception {
        cannotOverwrite("secret");
    }

    /** All known means of creating an item under a new name. */
    private enum OverwriteTactic {
        /** Use the REST command to create an empty project (normally used only from the UI in the New Item dialog). */
        REST_EMPTY {
            @Override void run(JenkinsRule r, String target) throws Exception {
                JenkinsRule.WebClient wc = wc(r)
                        // redirect perversely counts as a failure
                        .withRedirectEnabled(false)
                        .withThrowExceptionOnFailingStatusCode(false);
                WebResponse webResponse = wc.getPage(new WebRequest(new URI(wc.getContextPath() + "createItem?name=" + target + "&mode=hudson.model.FreeStyleProject").toURL(), HttpMethod.POST)).getWebResponse();
                if (webResponse.getStatusCode() != HttpURLConnection.HTTP_MOVED_TEMP) {
                    throw new FailingHttpStatusCodeException(webResponse);
                }
            }
        },
        /** Use the REST command to copy an existing project (normally used from the UI in the New Item dialog). */
        REST_COPY {
            @Override void run(JenkinsRule r, String target) throws Exception {
                r.createFreeStyleProject("dupe");
                JenkinsRule.WebClient wc = wc(r)
                        .withRedirectEnabled(false)
                        .withThrowExceptionOnFailingStatusCode(false);
                WebResponse webResponse = wc.getPage(new WebRequest(new URI(wc.getContextPath() + "createItem?name=" + target + "&mode=copy&from=dupe").toURL(), HttpMethod.POST)).getWebResponse();
                r.jenkins.getItem("dupe").delete();
                if (webResponse.getStatusCode() != HttpURLConnection.HTTP_MOVED_TEMP) {
                    throw new FailingHttpStatusCodeException(webResponse);
                }
            }
        },
        /** Overwrite target using REST command to create a project from XML submission. */
        REST_CREATE {
            @Override void run(JenkinsRule r, String target) throws Exception {
                JenkinsRule.WebClient wc = wc(r);
                WebRequest req = new WebRequest(new URI(wc.getContextPath() + "createItem?name=" + target).toURL(), HttpMethod.POST);
                req.setAdditionalHeader("Content-Type", "application/xml");
                req.setRequestBody("<project/>");
                wc.getPage(req);
            }
        },
        /** Overwrite target using REST command to rename an existing project (normally used from the UI in the Configure screen). */
        REST_RENAME {
            @Override void run(JenkinsRule r, String target) throws Exception {
                r.createFreeStyleProject("dupe");
                JenkinsRule.WebClient wc = wc(r)
                        .withRedirectEnabled(false)
                        .withThrowExceptionOnFailingStatusCode(false);
                WebResponse webResponse = wc.getPage(new WebRequest(new URI(wc.getContextPath() + "job/dupe/doRename?newName=" + target).toURL(), HttpMethod.POST)).getWebResponse();
                if (webResponse.getStatusCode() != HttpURLConnection.HTTP_MOVED_TEMP) {
                    r.jenkins.getItem("dupe").delete();
                    throw new FailingHttpStatusCodeException(webResponse);
                }
                assertNull(r.jenkins.getItem("dupe"));
            }
        },
        /** Overwrite target using the CLI {@code create-job} command. */
        CLI_CREATE {
            @Override void run(JenkinsRule r, String target) throws Exception {
                CLICommand cmd = new CreateJobCommand();
                CLICommandInvoker invoker = new CLICommandInvoker(r, cmd);
                cmd.setTransportAuth2(User.getOrCreateByIdOrFullName("attacker").impersonate2());
                int status = invoker.withStdin(new ByteArrayInputStream("<project/>".getBytes(StandardCharsets.UTF_8))).invokeWithArgs(target).returnCode();
                if (status != 0) {
                    throw new AbortException("CLI command failed with status " + status);
                }
            }
        },
        /** Overwrite target using the CLI {@code copy-job} command. */
        CLI_COPY {
            @Override void run(JenkinsRule r, String target) throws Exception {
                r.createFreeStyleProject("dupe");
                CLICommand cmd = new CopyJobCommand();
                CLICommandInvoker invoker = new CLICommandInvoker(r, cmd);
                cmd.setTransportAuth2(User.getOrCreateByIdOrFullName("attacker").impersonate2());
                int status = invoker.invokeWithArgs("dupe", target).returnCode();
                r.jenkins.getItem("dupe").delete();
                if (status != 0) {
                    throw new AbortException("CLI command failed with status " + status);
                }
            }
        },
        /** Overwrite target using a move function normally called from {@code cloudbees-folder} via a {@code move} action. */
        MOVE {
            @Override void run(JenkinsRule r, String target) throws Exception {
                try {
                    SecurityContext orig = ACL.impersonate2(User.getOrCreateByIdOrFullName("attacker").impersonate2());
                    try {
                        Items.move(r.jenkins.getItemByFullName("d", MockFolder.class).createProject(FreeStyleProject.class, target), r.jenkins);
                    } finally {
                        SecurityContextHolder.setContext(orig);
                    }
                    assertNull(r.jenkins.getItemByFullName("d/" + target));
                } catch (Exception x) {
                    r.jenkins.getItemByFullName("d/" + target).delete();
                    throw x;
                }
            }
        };
        abstract void run(JenkinsRule r, String target) throws Exception;

        private static JenkinsRule.WebClient wc(JenkinsRule r) {
            return r.createWebClient().withBasicApiToken("attacker");
        }
    }

}
