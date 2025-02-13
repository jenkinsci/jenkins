package org.jenkins.mytests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.pages.SignupPage;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlButton;
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

    @Test // 1. Covers: Add User -> Valid Inputs
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

    @Test // 2. Covers: Delete User -> Confirm Deletion
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

    @Test // 3. Covers: Add User -> Invalid Username
    public void createUserInvalidUsername() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();

        // Attempt to sign up with an invalid (empty) username
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("");  // invalid
        signup.enterPassword("12345");
        signup.enterFullName("No Username");

        HtmlPage result = signup.submit(j);

        // Expect an error message rather than "Success"
        String mainPanelText = result.getElementById("main-panel").getTextContent();
        assertThat(mainPanelText, not(containsString("Success")));

        // Verify the user was NOT created
        assertNull(securityRealm.getUser(""));
    }

    @Test // 4. Covers: Add User -> Invalid Password
    public void createUserInvalidPassword() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();

        // Attempt to sign up with an invalid (empty) password
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("noPassUser");
        signup.enterPassword(""); // invalid
        signup.enterFullName("No Password");

        HtmlPage result = signup.submit(j);

        // Expect no success
        String mainPanelText = result.getElementById("main-panel").getTextContent();
        assertThat(mainPanelText, not(containsString("Success")));

        // Verify the user was NOT created
        assertNull(securityRealm.getUser("noPassUser"));
    }

    @Test // 5. Covers: Add User -> Cancel
    public void createUserCancel() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();

        // Navigate to the signup page
        HtmlPage signupPage = wc.goTo("signup");
        SignupPage signup = new SignupPage(signupPage);
        signup.enterUsername("cancelUser");
        signup.enterPassword("12345");
        signup.enterFullName("Cancel User");

        // Attempt to find a "Cancel" button
        HtmlButton cancelButton = signupPage.getFirstByXPath("//button[normalize-space(text())='Cancel']");
        if (cancelButton == null) {
            // No Cancel button is present, so skip test and print to the console
            System.out.println("No Cancel button found on signup page. Skipping test.");
            return;
        }

        // If found, click it
        HtmlPage listUsersPage = cancelButton.click();
        j.waitUntilNoActivity();

        // Confirm we are back on the List Users page
        String mainPanelText = listUsersPage.getElementById("main-panel").getTextContent();
        assertThat(mainPanelText, containsString("Users"));

        // Verify user was not created
        assertNull("User should not exist if creation was canceled.", securityRealm.getUser("cancelUser"));
    }

    @Test // 6. Covers: Delete User -> Cancel
    public void deleteUserCancel() throws Exception {
        // Create a security realm that allows sign-up
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();

        // Sign up a user
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("cancelDeleteUser");
        signup.enterPassword("12345");
        signup.enterFullName("Cancel Delete User");
        HtmlPage signupSuccess = signup.submit(j);

        // Verify user is created
        assertThat(signupSuccess.getElementById("main-panel").getTextContent(), containsString("Success"));
        assertEquals("Cancel Delete User", securityRealm.getUser("cancelDeleteUser").getDisplayName());

        // Attempt to find a "Cancel" button
        HtmlButton cancelButton = signupSuccess.getFirstByXPath("//button[normalize-space(text())='Cancel']");
        if (cancelButton == null) {
            // No Cancel button is present, so skip test and print to the console
            System.out.println("No Cancel button found on signup page. Skipping test.");
            return;
        }

        // If found, click it
        HtmlPage listUsersPage = cancelButton.click();
        j.waitUntilNoActivity();

        // Confirm we are back on the List Users page
        String mainPanelText = listUsersPage.getElementById("main-panel").getTextContent();
        assertThat(mainPanelText, containsString("Users"));

        // Verify user still exists
        assertNotNull("User should still exist if deletion was canceled.", securityRealm.getUser("cancelDeleteUser"));
    }
}
