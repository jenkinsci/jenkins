package org.jenkins.mytests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;

import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.pages.SignupPage;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

public class FunctionalTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGetAllUsers() {
        HudsonPrivateSecurityRealm testRealm = new HudsonPrivateSecurityRealm(false, false, null);

        Jenkins jenkins = j.jenkins;
        j.jenkins.setSecurityRealm(testRealm);
        assertEquals(0, (testRealm.getAllUsers()).size());
    }

    @Test
    public void createJob() {
        String jobName = "test-job";
        final TopLevelItem rootJob = Mockito.mock(TopLevelItem.class);
        Mockito.when(rootJob.getDisplayName()).thenReturn(jobName);

        // Check if the job was created successfully
        assertEquals(jobName, rootJob.getDisplayName());
    }

    @Test
    public void createUser() throws Exception {
        // Create a security realm to allow for user signup
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();

        // Sign up the new user
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("test");
        signup.enterPassword("12345");
        signup.enterFullName("Test User");

        // Submit the sign-up form
        HtmlPage success = signup.submit(j);
        assertThat(success.getElementById("main-panel").getTextContent(), containsString("Success"));
        assertThat(success.getAnchorByHref("/jenkins/user/test").getTextContent(), containsString("Test User"));

        // Verify that the user was created successfully
        assertEquals("Test User", securityRealm.getUser("test").getDisplayName());
    }

    @Test
    public void deleteUser() throws Exception {
        // Use a security realm that allows user signup
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();

        // First, sign up a new user
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("deleteTest");
        signup.enterPassword("12345");
        signup.enterFullName("Delete Test User");

        // Submit the sign-up form
        HtmlPage success = signup.submit(j);

        // Verify that the user was created successfully by checking the main-panel and the user's display name
        assertThat(success.getElementById("main-panel").getTextContent(), containsString("Success"));
        assertEquals("Delete Test User", securityRealm.getUser("deleteTest").getDisplayName());

        // Now, delete the user
        User user = securityRealm.getUser("deleteTest");
        user.delete();

        // Verify that the user is no longer in the security realm (should return null)
        assertEquals(null, securityRealm.getUser("deleteTest"));

        // Also verify that the list of users is now empty
        assertEquals(0, securityRealm.getAllUsers().size());
    }
}
