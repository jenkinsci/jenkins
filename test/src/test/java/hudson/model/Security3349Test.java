package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class Security3349Test {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Rule public FlagRule<Boolean> skipPermissionCheck = new FlagRule<>(() -> MyViewsProperty.SKIP_PERMISSION_CHECK, x -> MyViewsProperty.SKIP_PERMISSION_CHECK = x);

    @Test
    @Issue("SECURITY-3349")
    public void usersCannotAccessOtherUsersViews() throws Exception {
        User user = User.getOrCreateByIdOrFullName("user");
        User admin = User.getOrCreateByIdOrFullName("admin");

        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        MockAuthorizationStrategy mockAuthorizationStrategy = new MockAuthorizationStrategy();
        mockAuthorizationStrategy.grant(Jenkins.READ, View.READ).everywhere().to("user");
        mockAuthorizationStrategy.grant(Jenkins.ADMINISTER).everywhere().to("admin");
        rule.jenkins.setAuthorizationStrategy(mockAuthorizationStrategy);

        MyViewsProperty prop1 = new MyViewsProperty(null);
        MyView usersView = new MyView("User's view", prop1);
        user.addProperty(prop1);
        prop1.setUser(user);
        prop1.addView(usersView);

        MyViewsProperty prop2 = new MyViewsProperty(null);
        MyView adminsView = new MyView("Admin's view", prop2);
        admin.addProperty(prop2);
        prop2.setUser(admin);
        prop2.addView(adminsView);

        try (JenkinsRule.WebClient wc = rule.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            wc.login("user");

            HtmlPage adminViews = wc.goTo("user/admin/my-views/view/all/");
            assertEquals(403, adminViews.getWebResponse().getStatusCode());

            HtmlPage adminUserPage = wc.goTo("user/admin/");
            assertFalse(adminUserPage.getVisibleText().contains("My Views"));

            HtmlPage userViews = wc.goTo("user/user/my-views/view/all/");
            assertEquals(200, userViews.getWebResponse().getStatusCode());

            HtmlPage userUserPage = wc.goTo("user/user/");
            assertTrue(userUserPage.getVisibleText().contains("My Views"));

            wc.login("admin");

            adminViews = wc.goTo("user/admin/my-views/view/all/");
            assertEquals(200, adminViews.getWebResponse().getStatusCode());
            userViews = wc.goTo("user/user/my-views/view/all/");
            assertEquals(200, userViews.getWebResponse().getStatusCode());

            MyViewsProperty.SKIP_PERMISSION_CHECK = true;

            wc.login("user");
            adminViews = wc.goTo("user/admin/my-views/view/all/");
            assertEquals(200, adminViews.getWebResponse().getStatusCode());
            adminUserPage = wc.goTo("user/admin/");
            assertTrue(adminUserPage.getVisibleText().contains("My Views"));

        }
    }
}
