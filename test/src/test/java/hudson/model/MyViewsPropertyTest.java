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

import org.junit.Test;
import org.junit.Rule;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.model.Descriptor.FormException;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.context.SecurityContextHolder;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;
/**
 *
 * @author Lucie Votypkova
 */
public class MyViewsPropertyTest {
   
    @Rule
    public JenkinsRule rule = new JenkinsRule();
    
    @Test
    public void testReadResolve() throws IOException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.setUser(user);
        user.addProperty(property);
        property.readResolve();
        assertNotNull("Property should contains " + Messages.Hudson_ViewName() + " defaultly.", property.getView(Messages.Hudson_ViewName()));
    }
    
    @Test
    public void testSave() throws IOException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
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
    
    @Test
    public void testGetViews() throws IOException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.readResolve();
        property.setUser(user);
        assertTrue("Property should contains " + Messages.Hudson_ViewName(), property.getViews().contains(property.getView(Messages.Hudson_ViewName())));
        View view = new ListView("foo",property);
        property.addView(view);
        assertTrue("Property should contains " + view.name, property.getViews().contains(view));
    }
    
    @Test
    public void testGetView() throws IOException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.readResolve();
        property.setUser(user);
        assertNotNull("Property should contains " + Messages.Hudson_ViewName(), property.getView(Messages.Hudson_ViewName()));
        View view = new ListView("foo",property);
        property.addView(view);
        assertEquals("Property should contains " + view.name, view, property.getView(view.name));
    }
    
    @Test
    public void testGetPrimaryView() throws IOException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        assertEquals("Property should have primary view " + Messages.Hudson_ViewName() + " instead of " + property.getPrimaryView(). name,property.getView(Messages.Hudson_ViewName()), property.getPrimaryView());
        View view = new ListView("foo", property);
        property.addView(view);
        property.setPrimaryViewName(view.name);
        assertEquals("Property should have primary view " + view.name + " instead of " + property.getPrimaryView().name, view, property.getPrimaryView());
    }
    
    @Test
    public void testCanDelete() throws IOException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        assertFalse("Property should not enable to delete view " + Messages.Hudson_ViewName(), property.canDelete(property.getView(Messages.Hudson_ViewName())));
        View view = new ListView("foo", property);
        property.addView(view);
        assertTrue("Property should enable to delete view " + view.name , property.canDelete(view));
        property.setPrimaryViewName(view.name);
        assertFalse("Property should not enable to delete view " + view.name , property.canDelete(view));
        assertTrue("Property should enable to delete view " + Messages.Hudson_ViewName(), property.canDelete(property.getView(Messages.Hudson_ViewName())));
    }

    @Test
    public void testDeleteView() throws IOException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        boolean ex = false;
        try{
            property.deleteView(property.getView(Messages.Hudson_ViewName()));
        }
        catch(IllegalStateException e){
            ex = true;
        }
        assertTrue("Property should throw IllegalStateException.", ex);
        assertTrue("Property should contain view " + Messages.Hudson_ViewName(), property.getViews().contains(property.getView(Messages.Hudson_ViewName())));
        View view = new ListView("foo", property);
        property.addView(view);
        ex = false;
        try{
            property.deleteView(view);
        }
        catch(IllegalStateException e){
            ex = true;
        }
        assertFalse("Property should not contain view " + view.name , property.getViews().contains(view));
        property.addView(view);
        property.setPrimaryViewName(view.name);
        assertTrue("Property should not contain view " + view.name , property.getViews().contains(view));
        property.deleteView(property.getView(Messages.Hudson_ViewName()));
        assertFalse("Property should not contains view " + Messages.Hudson_ViewName(), property.getViews().contains(property.getView(Messages.Hudson_ViewName())));
    }

    @Test
    public void testOnViewRenamed() throws IOException, Failure, FormException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
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
    public void testAddView() throws IOException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        View view = new ListView("foo", property);
        property.addView(view);
        assertTrue("Property should contians view " + view.name, property.getViews().contains(view));
        User.reload();
        user = User.get("User");
        property = user.getProperty(property.getClass());
        assertTrue("Property should save changes.", property.getViews().contains(property.getView(view.name)));
    }

    @Test
    public void testDoCreateView() throws Exception {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        HtmlForm form = rule.createWebClient().goTo(property.getUrl() + "/newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("foo");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        rule.submit(form);
        assertNotNull("Property should contain view foo", property.getView("foo")); 
        User.reload();
        property = User.get("User").getProperty(property.getClass());
        assertNotNull("Property should save changes", property.getView("foo"));
    }

    @Test
    public void testGetACL() throws IOException {
        User user = User.get("User");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.readResolve();
        property.setUser(user);
        user.addProperty(property);
        for(Permission p : Permission.getAll()){
            assertEquals("Property should have the same ACL as its user", property.getACL().hasPermission(p), user.getACL().hasPermission(p));
        }
    }

    @Test
    public void testCheckPermission() throws IOException {
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        User user = User.get("User");
        User user2 = User.get("User2");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.readResolve();
        property.setUser(user);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        rule.jenkins.setAuthorizationStrategy(auth);     
        user.addProperty(property);
        boolean ex = false;
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate());
        try{
            property.checkPermission(Permission.CONFIGURE);
        }
        catch(AccessDeniedException e){
            ex = true;
        }
        assertTrue("Property should throw AccessDeniedException.",ex);
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        try{
            property.checkPermission(Permission.CONFIGURE);
        }
        catch(AccessDeniedException e){
            fail("Property should not throw AccessDeniedException - user should control of himself.");
        }
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate());
        auth.add(Jenkins.ADMINISTER, "User2");
        try{
            property.checkPermission(Permission.CONFIGURE);
        }
        catch(AccessDeniedException e){
            fail("Property should not throw AccessDeniedException.");
        }
    }

    @Test
    public void testHasPermission() throws IOException {
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        User user = User.get("User");
        User user2 = User.get("User2");
        MyViewsProperty property = new MyViewsProperty(Messages.Hudson_ViewName());
        property.readResolve();
        property.setUser(user);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        rule.jenkins.setAuthorizationStrategy(auth);    
        user.addProperty(property);
        SecurityContextHolder.getContext().setAuthentication(user2.impersonate());
        assertFalse("User User2 should not configure permission for user User",property.hasPermission(Permission.CONFIGURE));
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        assertTrue("User should control of himself.", property.hasPermission(Permission.CONFIGURE));
        auth.add(Jenkins.ADMINISTER, "User2");
        assertTrue("User User2 should configure permission for user User",property.hasPermission(Permission.CONFIGURE));
    }
    
}
