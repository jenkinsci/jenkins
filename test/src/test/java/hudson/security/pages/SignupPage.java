package hudson.security.pages;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

/**
 * The the signup page for {@link hudson.security.HudsonPrivateSecurityRealm}
 */
public class SignupPage {

    public HtmlForm signupForm;
    public final HtmlPage signupPage;

    public SignupPage(HtmlPage signupPage) {
        this.signupPage = signupPage;

        assertNotNull("The sign up page has a username field.", this.signupPage.getElementById("username"));
        for (HtmlForm signupForm : this.signupPage.getForms()) {
            if (signupForm.getInputsByName("username").size() == 0)
                continue;
            this.signupForm = signupForm;
        }

    }



    public void enterUsername(String username) {
        signupForm.getInputByName("username").setValueAttribute(username);
    }

    /**
     * Enters the password in password1 and password2.
     * You can then call {@link #enterPassword2(String)} if you want them to be different.
     * @param password
     */
    public void enterPassword(String password) {
        signupForm.getInputByName("password1").setValueAttribute(password);
        signupForm.getInputByName("password2").setValueAttribute(password);
    }

    public void enterPassword2(String password2) {
        signupForm.getInputByName("password2").setValueAttribute(password2);
    }

    public void enterFullName(String fullName) {
        signupForm.getInputByName("fullname").setValueAttribute(fullName);
    }

    public void enterEmail(String email) {
        signupForm.getInputByName("email").setValueAttribute(email);
    }

    public HtmlPage submit(JenkinsRule rule) throws Exception {
        return rule.submit(signupForm);
    }

    public void assertErrorContains(String msg) {
        assertThat(signupForm.getElementById("main-panel").getTextContent(),containsString(msg));
    }
}
