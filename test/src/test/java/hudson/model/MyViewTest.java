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

import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import java.io.IOException;
import org.acegisecurity.context.SecurityContextHolder;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

/**
 *
 * @author Lucie Votypkova
 */
public class MyViewTest {
    
    @Rule
    public JenkinsRule rule = new JenkinsRule();
    
    @Test
    public void testContains() throws IOException, Exception{
        
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        rule.jenkins.setAuthorizationStrategy(auth);
        User user = User.get("User1");
        FreeStyleProject job = rule.createFreeStyleProject("job");
        MyView view = new MyView("My", rule.jenkins);
        rule.jenkins.addView(view);
        auth.add(Job.READ, "User1");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        assertFalse("View " + view.getDisplayName() + " should not contain job " + job.getDisplayName(), view.contains(job));
        auth.add(Job.CONFIGURE, "User1");
        assertTrue("View " + view.getDisplayName() + " contain job " + job.getDisplayName(), view.contains(job));
    }
    
    @Test
    public void testDoCreateItem() throws IOException, Exception{
        MyView view = new MyView("My", rule.jenkins);
        rule.jenkins.addView(view);
        HtmlForm form = rule.createWebClient().goTo("view/" + view.getDisplayName() + "/newJob").getFormByName("createItem");
        form.getInputsByValue("hudson.model.FreeStyleProject").get(0).setChecked(true);
        form.getInputByName("name").setValueAttribute("job");
        rule.submit(form);
        Item item = rule.jenkins.getItem("job");
        assertTrue("View " + view.getDisplayName() + " should contain job " + item.getDisplayName(), view.getItems().contains(item)); 
    }
    
    @Test
    public void testGetItems() throws IOException, InterruptedException{
        User user = User.get("User1");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        rule.jenkins.setAuthorizationStrategy(auth);   
        FreeStyleProject job2 = rule.createFreeStyleProject("job2");
        FreeStyleProject job = rule.createFreeStyleProject("job");
        MyView view = new MyView("My", rule.jenkins);
        auth.add(Job.READ, "User1");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        assertFalse("View " + view.getDisplayName() + " should not contains job " + job.getDisplayName(), view.getItems().contains(job));
        assertFalse("View " + view.getDisplayName() + " should not contains job " + job2.getDisplayName(), view.getItems().contains(job2));
        auth.add(Job.CONFIGURE, "User1");
        assertTrue("View " + view.getDisplayName() + " should contains job " + job.getDisplayName(), view.getItems().contains(job));
        assertTrue("View " + view.getDisplayName() + " should contains job " + job2.getDisplayName(), view.getItems().contains(job2));
    }
    
    
}