/*
 * The MIT License
 *
 * Copyright 2013 Red Hat, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.model.Descriptor.FormException;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlForm;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 *
 * @author Lucie Votypkova
 */
public class MyViewsPropertyTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void testReadResolve() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.setUser(user);
        user.addProperty(property);
        property.readResolve();
        assertNotNull("Property should contain " + AllView.DEFAULT_VIEW_NAME + " by default.", property.getView(AllView.DEFAULT_VIEW_NAME));
    }

    /* TODO unclear what exactly this is purporting to assert
    @Test
    public void testSave() throws IOException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        View view = new ListView("foo", property);
        property.addView(view);
        property.setPrimaryViewName(view.name);
        User.reload();
        property = User.get("User").getProperty(property.getClass());
        assertNotSame("Property should not have primary view " + view.name, view.name, property.getPrimaryViewName());
        property.setUser(user);
        property.addView(view);
        property.setPrimaryViewName(view.name);
        property.save();
        User.reload();
        property = User.get("User").getProperty(property.getClass());
        assertEquals("Property should have primary view " + view.name + " instead of " + property.getPrimaryViewName(), view.name, property.getPrimaryViewName());
    }
    */

    @Test
    public void testGetViews() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        assertTrue("Property should contain " + AllView.DEFAULT_VIEW_NAME, property.getViews().contains(property.getView(AllView.DEFAULT_VIEW_NAME)));
        View view = new ListView("foo", property);
        property.addView(view);
        assertTrue("Property should contain " + view.name, property.getViews().contains(view));
    }

    @Test
    public void testGetView() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        assertNotNull("Property should contain " + AllView.DEFAULT_VIEW_NAME, property.getView(
                AllView.DEFAULT_VIEW_NAME));
        View view = new ListView("foo", property);
        property.addView(view);
        assertEquals("Property should contain " + view.name, view, property.getView(view.name));
    }

    @Test
    public void testGetPrimaryView() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        assertEquals("Property should have primary view " + AllView.DEFAULT_VIEW_NAME + " instead of " + property.getPrimaryView(). name, property.getView(AllView.DEFAULT_VIEW_NAME), property.getPrimaryView());
        View view = new ListView("foo", property);
        property.addView(view);
        property.setPrimaryViewName(view.name);
        assertEquals("Property should have primary view " + view.name + " instead of " + property.getPrimaryView().name, view, property.getPrimaryView());
    }

    @Test
    public void testCanDelete() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        assertFalse("Property should not enable to delete view " + AllView.DEFAULT_VIEW_NAME, property.canDelete(property.getView(AllView.DEFAULT_VIEW_NAME)));
        View view = new ListView("foo", property);
        property.addView(view);
        assertTrue("Property should enable to delete view " + view.name, property.canDelete(view));
        property.setPrimaryViewName(view.name);
        assertFalse("Property should not enable to delete view " + view.name, property.canDelete(view));
        assertTrue("Property should enable to delete view " + AllView.DEFAULT_VIEW_NAME, property.canDelete(property.getView(AllView.DEFAULT_VIEW_NAME)));
    }

    @Test
    public void testDeleteView() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        boolean ex = false;
        try {
            property.deleteView(property.getView(AllView.DEFAULT_VIEW_NAME));
        }
        catch (IllegalStateException e) {
            ex = true;
        }
        assertTrue("Property should throw IllegalStateException.", ex);
        assertTrue("Property should contain view " + AllView.DEFAULT_VIEW_NAME, property.getViews().contains(property.getView(AllView.DEFAULT_VIEW_NAME)));
        View view = new ListView("foo", property);
        property.addView(view);
        ex = false;
        try {
            property.deleteView(view);
        }
        catch (IllegalStateException e) {
            ex = true;
        }
        assertFalse("Property should not contain view " + view.name, property.getViews().contains(view));
        property.addView(view);
        property.setPrimaryViewName(view.name);
        assertTrue("Property should not contain view " + view.name, property.getViews().contains(view));
        property.deleteView(property.getView(AllView.DEFAULT_VIEW_NAME));
        assertFalse("Property should not contains view " + AllView.DEFAULT_VIEW_NAME, property.getViews().contains(property.getView(AllView.DEFAULT_VIEW_NAME)));
    }

    @Test
    public void testOnViewRenamed() throws IOException, FormException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        View view = new ListView("foo", property);
        property.addView(view);
        property.setPrimaryViewName(view.name);
        view.rename("primary-renamed");
        assertEquals("Property should rename its primary view ", "primary-renamed", property.getPrimaryViewName());
    }

    @Test
    public void testAddView() throws Exception {
        {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        View view = new ListView("foo", property);
        property.addView(view);
        assertTrue("Property should contain view " + view.name, property.getViews().contains(view));
        }
        rule.jenkins.reload();
        {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = user.getProperty(MyViewsProperty.class);
        assertTrue("Property should save changes.", property.getViews().contains(property.getView("foo")));
        }
    }

    @Test
    public void testDoCreateView() throws Exception {
        {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        HtmlForm form = rule.createWebClient().goTo(property.getUrl() + "/newView").getFormByName("createItem");
        form.getInputByName("name").setValue("foo");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        rule.submit(form);
        assertNotNull("Property should contain view foo", property.getView("foo"));
        }
        rule.jenkins.reload();
        {
        MyViewsProperty property = User.getOrCreateByIdOrFullName("User").getProperty(MyViewsProperty.class);
        assertNotNull("Property should save changes", property.getView("foo"));
        }
    }

    @Test
    public void testGetACL() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        for (Permission p : Permission.getAll()) {
            assertEquals("Property should have the same ACL as its user", property.hasPermission(p), user.hasPermission(p));
        }
    }

    @Test
    public void testCheckPermission() throws IOException {
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        User user = User.getOrCreateByIdOrFullName("User");
        User user2 = User.getOrCreateByIdOrFullName("User2");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(auth);
        user.addProperty(property);
        boolean ex = false;
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate2());
        try {
            property.checkPermission(Permission.CONFIGURE);
        }
        catch (AccessDeniedException e) {
            ex = true;
        }
        assertTrue("Property should throw AccessDeniedException.", ex);
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        try {
            property.checkPermission(Permission.CONFIGURE);
        }
        catch (AccessDeniedException e) {
            fail("Property should not throw AccessDeniedException - user should control of himself.");
        }
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate2());
        auth.add(Jenkins.ADMINISTER, "User2");
        try {
            property.checkPermission(Permission.CONFIGURE);
        }
        catch (AccessDeniedException e) {
            fail("Property should not throw AccessDeniedException.");
        }
    }

    @Test
    public void testHasPermission() throws IOException {
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        User user = User.getOrCreateByIdOrFullName("User");
        User user2 = User.getOrCreateByIdOrFullName("User2");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        rule.jenkins.setAuthorizationStrategy(auth);
        user.addProperty(property);
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate2());
        assertFalse("User User2 should not configure permission for user User", property.hasPermission(Permission.CONFIGURE));
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        assertTrue("User should control of himself.", property.hasPermission(Permission.CONFIGURE));
        auth.add(Jenkins.ADMINISTER, "User2");
        assertTrue("User User2 should configure permission for user User", property.hasPermission(Permission.CONFIGURE));
    }

    @Test
    @Issue("JENKINS-48157")
    public void shouldNotFailWhenMigratingLegacyViewsWithoutPrimaryOne() throws IOException {
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        User user = User.getOrCreateByIdOrFullName("User");

        // Emulates creation of a new object with Reflection in User#load() does.
        MyViewsProperty property = new MyViewsProperty(null);
        user.addProperty(property);

        // At AllView with non-default to invoke NPE path in AllView.migrateLegacyPrimaryAllViewLocalizedName()
        property.addView(new AllView("foobar"));
        property.readResolve();
    }

}
