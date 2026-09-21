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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Descriptor.FormException;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 *
 * @author Lucie Votypkova
 */
@WithJenkins
class MyViewsPropertyTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        rule = j;
    }

    @Test
    void testReadResolve() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.setUser(user);
        user.addProperty(property);
        property.readResolve();
        assertNotNull(property.getView(AllView.DEFAULT_VIEW_NAME), "Property should contain " + AllView.DEFAULT_VIEW_NAME + " by default.");
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
    void testGetViews() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        assertTrue(property.getViews().contains(property.getView(AllView.DEFAULT_VIEW_NAME)), "Property should contain " + AllView.DEFAULT_VIEW_NAME);
        View view = new ListView("foo", property);
        property.addView(view);
        assertTrue(property.getViews().contains(view), "Property should contain " + view.name);
    }

    @Test
    void testGetView() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        assertNotNull(property.getView(
                AllView.DEFAULT_VIEW_NAME), "Property should contain " + AllView.DEFAULT_VIEW_NAME);
        View view = new ListView("foo", property);
        property.addView(view);
        assertEquals(view, property.getView(view.name), "Property should contain " + view.name);
    }

    @Test
    void testGetPrimaryView() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        assertEquals(property.getView(AllView.DEFAULT_VIEW_NAME), property.getPrimaryView(), "Property should have primary view " + AllView.DEFAULT_VIEW_NAME + " instead of " + property.getPrimaryView(). name);
        View view = new ListView("foo", property);
        property.addView(view);
        property.setPrimaryViewName(view.name);
        assertEquals(view, property.getPrimaryView(), "Property should have primary view " + view.name + " instead of " + property.getPrimaryView().name);
    }

    @Test
    void testCanDelete() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        assertFalse(property.canDelete(property.getView(AllView.DEFAULT_VIEW_NAME)), "Property should not enable to delete view " + AllView.DEFAULT_VIEW_NAME);
        View view = new ListView("foo", property);
        property.addView(view);
        assertTrue(property.canDelete(view), "Property should enable to delete view " + view.name);
        property.setPrimaryViewName(view.name);
        assertFalse(property.canDelete(view), "Property should not enable to delete view " + view.name);
        assertTrue(property.canDelete(property.getView(AllView.DEFAULT_VIEW_NAME)), "Property should enable to delete view " + AllView.DEFAULT_VIEW_NAME);
    }

    @Test
    void testDeleteView() throws IOException {
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
        assertTrue(ex, "Property should throw IllegalStateException.");
        assertTrue(property.getViews().contains(property.getView(AllView.DEFAULT_VIEW_NAME)), "Property should contain view " + AllView.DEFAULT_VIEW_NAME);
        View view = new ListView("foo", property);
        property.addView(view);
        ex = false;
        try {
            property.deleteView(view);
        }
        catch (IllegalStateException e) {
            ex = true;
        }
        assertFalse(property.getViews().contains(view), "Property should not contain view " + view.name);
        property.addView(view);
        property.setPrimaryViewName(view.name);
        assertTrue(property.getViews().contains(view), "Property should not contain view " + view.name);
        property.deleteView(property.getView(AllView.DEFAULT_VIEW_NAME));
        assertFalse(property.getViews().contains(property.getView(AllView.DEFAULT_VIEW_NAME)), "Property should not contains view " + AllView.DEFAULT_VIEW_NAME);
    }

    @Test
    void testOnViewRenamed() throws IOException, FormException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        View view = new ListView("foo", property);
        property.addView(view);
        property.setPrimaryViewName(view.name);
        view.rename("primary-renamed");
        assertEquals("primary-renamed", property.getPrimaryViewName(), "Property should rename its primary view ");
    }

    @Test
    void testAddView() throws Exception {
        {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        View view = new ListView("foo", property);
        property.addView(view);
        assertTrue(property.getViews().contains(view), "Property should contain view " + view.name);
        }
        rule.jenkins.reload();
        {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = user.getProperty(MyViewsProperty.class);
        assertTrue(property.getViews().contains(property.getView("foo")), "Property should save changes.");
        }
    }

    @Test
    void testDoCreateView() throws Exception {
        {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        HtmlForm form = rule.createWebClient().goTo(property.getUrl() + "/newView").getFormByName("createItem");
        form.getInputByName("name").setValue("foo");
        form.getRadioButtonsByName("mode").getFirst().setChecked(true);
        rule.submit(form);
        assertNotNull(property.getView("foo"), "Property should contain view foo");
        }
        rule.jenkins.reload();
        {
        MyViewsProperty property = User.getOrCreateByIdOrFullName("User").getProperty(MyViewsProperty.class);
        assertNotNull(property.getView("foo"), "Property should save changes");
        }
    }

    @Test
    void testGetACL() throws IOException {
        User user = User.getOrCreateByIdOrFullName("User");
        MyViewsProperty property = new MyViewsProperty(AllView.DEFAULT_VIEW_NAME);
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        for (Permission p : Permission.getAll()) {
            assertEquals(property.hasPermission(p), user.hasPermission(p), "Property should have the same ACL as its user");
        }
    }

    @Test
    void testCheckPermission() throws IOException {
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
        assertTrue(ex, "Property should throw AccessDeniedException.");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        assertDoesNotThrow(() -> property.checkPermission(Permission.CONFIGURE), "Property should not throw AccessDeniedException - user should control of himself.");
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate2());
        auth.add(Jenkins.ADMINISTER, "User2");
        assertDoesNotThrow(() -> property.checkPermission(Permission.CONFIGURE), "Property should not throw AccessDeniedException.");
    }

    @Test
    void testHasPermission() throws IOException {
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
        assertFalse(property.hasPermission(Permission.CONFIGURE), "User User2 should not configure permission for user User");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());
        assertTrue(property.hasPermission(Permission.CONFIGURE), "User should control of himself.");
        auth.add(Jenkins.ADMINISTER, "User2");
        assertTrue(property.hasPermission(Permission.CONFIGURE), "User User2 should configure permission for user User");
    }

    @Test
    @Issue("JENKINS-48157")
    void shouldNotFailWhenMigratingLegacyViewsWithoutPrimaryOne() throws IOException {
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
