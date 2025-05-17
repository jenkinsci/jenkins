/*
 * The MIT License
 *
 * Copyright 2013 Lucie Votypkova.
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.security.GlobalMatrixAuthorizationStrategy;
import java.io.IOException;
import java.util.logging.Level;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
class MyViewTest {

    private final LogRecorder logs = new LogRecorder();

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        rule = j;
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
    }

    @Test
    void testContains() throws Exception {

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(auth);
        User user = User.getOrCreateByIdOrFullName("User1");
        FreeStyleProject job = rule.createFreeStyleProject("job");
        MyView view = new MyView("My", rule.jenkins);
        rule.jenkins.addView(view);
        auth.add(Item.READ, "User1");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        assertFalse(view.contains(job), "View " + view.getDisplayName() + " should not contain job " + job.getDisplayName());
        auth.add(Item.CONFIGURE, "User1");
        assertTrue(view.contains(job), "View " + view.getDisplayName() + " contain job " + job.getDisplayName());
    }

    @Test
    void testDoCreateItem() throws Exception {
        logs.record(AbstractItem.class, Level.ALL);
        MyView view = new MyView("My", rule.jenkins);
        rule.jenkins.addView(view);
        HtmlPage newItemPage = rule.createWebClient().goTo("view/" + view.getDisplayName() + "/newJob");
        HtmlForm form = newItemPage.getFormByName("createItem");
        // Set the name of the item
        form.getInputByName("name").setValue("job");
        form.getInputByName("name").blur();
        // Select the item clicking on the first item type shown
        HtmlElement itemType = newItemPage.getFirstByXPath("//div[@class='category']/ul/li");
        itemType.click();
        rule.submit(form);
        Item item = rule.jenkins.getItem("job");
        assumeTrue(item != null, "TODO sometimes on Windows CI the submission does not seem to be really processed (most log messages are missing)");
        assertThat(view.getItems(), contains(equalTo(item)));
    }

    @Test
    void testGetItems() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User1");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(auth);
        FreeStyleProject job2 = rule.createFreeStyleProject("job2");
        FreeStyleProject job = rule.createFreeStyleProject("job");
        MyView view = new MyView("My", rule.jenkins);
        auth.add(Item.READ, "User1");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        assertFalse(view.getItems().contains(job), "View " + view.getDisplayName() + " should not contains job " + job.getDisplayName());
        assertFalse(view.getItems().contains(job2), "View " + view.getDisplayName() + " should not contains job " + job2.getDisplayName());
        auth.add(Item.CONFIGURE, "User1");
        assertTrue(view.getItems().contains(job), "View " + view.getDisplayName() + " should contain job " + job.getDisplayName());
        assertTrue(view.getItems().contains(job2), "View " + view.getDisplayName() + " should contain job " + job2.getDisplayName());
    }


}
