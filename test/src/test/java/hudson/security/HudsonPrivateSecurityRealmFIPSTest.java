/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc. and others
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

package hudson.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;

import hudson.logging.LogRecorder;
import hudson.logging.LogRecorderManager;
import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm.Details;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import jenkins.model.Jenkins;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlPasswordInput;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;


@For(HudsonPrivateSecurityRealm.class)
public class HudsonPrivateSecurityRealmFIPSTest {

    // the bcrypt encoded form of "passwordpassword" without the quotes
    private static final String JBCRYPT_ENCODED_PASSWORD = "#jbcrypt:$2a$10$Nm37vwdZwJ5T2QTBwYuBYONHD3qKilgd5UO7wuDXI83z5dAdrgi4i";

    private static final String LOG_RECORDER_NAME = "HPSR_LOG_RECORDER";

    @Rule
    public RealJenkinsRule rjr = new RealJenkinsRule().includeTestClasspathPlugins(false)
                                                       .javaOptions("-Xmx256M", "-Djenkins.security.FIPS140.COMPLIANCE=true");

    @Test
    public void generalLogin() throws Throwable {
        rjr.then(HudsonPrivateSecurityRealmFIPSTest::generalLoginStep);
    }

    private static void generalLoginStep(JenkinsRule j) throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        User u1 = securityRealm.createAccount("user", "passwordpassword");
        u1.setFullName("A User");
        u1.save();

        // we should be using PBKDF2 hasher
        String hashedPassword = u1.getProperty(Details.class).getPassword();
        assertThat(hashedPassword, startsWith("$PBKDF2$HMACSHA512:210000:"));

        try (WebClient wc = j.createWebClient()) {
            wc.login("user", "passwordpassword");
            assertThrows(FailingHttpStatusCodeException.class, () -> wc.login("user", "wrongPass123456"));
        }
    }

    @Test
    public void userCreationWithHashedPasswords() throws Throwable {
        rjr.then(HudsonPrivateSecurityRealmFIPSTest::userCreationWithHashedPasswordsStep);
    }

    private static void userCreationWithHashedPasswordsStep(JenkinsRule j) throws Exception {
        setupLogRecorder();
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        // "passwordpassword" after it has gone through the KDF
        securityRealm.createAccountWithHashedPassword("user_hashed",
                "$PBKDF2$HMACSHA512:210000:92857a190ac711436e8a9cb56595d642$0d66e61da0b04283148b4a574422a17762c96c46bcd3aa587b6d447a908367fe8030bd2083dc54313639561d36cc9ac707bed72fc3400465e7dc1d6805cffb66");

        try (WebClient wc = j.createWebClient()) {
            // login should succeed
            wc.login("user_hashed", "passwordpassword");
            assertThrows(FailingHttpStatusCodeException.class, () -> wc.login("user_hashed", "passwordpassword2"));
        }
        assertThat(getLogRecords(), not(hasItem(incorrectHashingLogEntry())));
    }

    @Test
    @LocalData
    public void userLoginAfterEnablingFIPS() throws Throwable {
        rjr.then(HudsonPrivateSecurityRealmFIPSTest::userLoginAfterEnablingFIPSStep);
    }

    private static void userLoginAfterEnablingFIPSStep(JenkinsRule j) throws Exception {
        setupLogRecorder();
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        assertThat(securityRealm.getUser("user").getProperty(Details.class).getPassword(), is(JBCRYPT_ENCODED_PASSWORD));

        try (WebClient wc = j.createWebClient()) {
            assertThrows(FailingHttpStatusCodeException.class, () -> wc.login("user", "passwordpassword"));
        }
        assertThat(getLogRecords(), hasItem(incorrectHashingLogEntry()));
    }

    @Test
    public void userCreationWithJBCryptPasswords() throws Throwable {
        rjr.then(HudsonPrivateSecurityRealmFIPSTest::userCreationWithJBCryptPasswordsStep);

    }

    private static void userCreationWithJBCryptPasswordsStep(JenkinsRule j) throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                () -> securityRealm.createAccountWithHashedPassword("user_hashed_incorrect_algorithm", JBCRYPT_ENCODED_PASSWORD));
        assertThat(illegalArgumentException.getMessage(),
                is("The hashed password was hashed with an incorrect algorithm. Jenkins is expecting $PBKDF2"));
    }

    @Test
    public void validatePasswordLengthForFIPS() throws Throwable {
        rjr.then(HudsonPrivateSecurityRealmFIPSTest::validatePasswordLengthForFIPSStep);
    }

    private static void validatePasswordLengthForFIPSStep(JenkinsRule j) throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        User u1 =  securityRealm.createAccount("test", "aValidFipsPass");

        WebClient wc = j.createWebClient();
        wc.login("test", "aValidFipsPass");

        HtmlPage configurePage = wc.goTo(u1.getUrl() + "/security/");
        HtmlPasswordInput password1 = configurePage.getElementByName("user.password");
        HtmlPasswordInput password2 = configurePage.getElementByName("user.password2");
        //Should fail as the password length is <14 (In FIPS mode)
        password1.setText("mockPassword");
        password2.setText("mockPassword");

        HtmlForm form = configurePage.getFormByName("config");
        assertThrows(FailingHttpStatusCodeException.class, () -> {
            j.submit(form);
        });
    }

    @Test
    public void validatePasswordMismatchForFIPS() throws Throwable {
        rjr.then(HudsonPrivateSecurityRealmFIPSTest::validatePasswordMismatchForFIPSStep);
    }

    private static void validatePasswordMismatchForFIPSStep(JenkinsRule j) throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        User u1 =  securityRealm.createAccount("test", "aValidFipsPass");


        WebClient wc = j.createWebClient();
        wc.login("test", "aValidFipsPass");

        HtmlPage configurePage = wc.goTo(u1.getUrl() + "/security/");
        HtmlPasswordInput password1 = configurePage.getElementByName("user.password");
        HtmlPasswordInput password2 = configurePage.getElementByName("user.password2");
        //should fail as the passwords are different (even though the password length >=14) In FIPS mode
        password1.setText("14charPassword");
        password2.setText("14charPa$$word");

        HtmlForm form = configurePage.getFormByName("config");
        assertThrows(FailingHttpStatusCodeException.class, () -> {
            j.submit(form);
        });
    }

    @Test
    public void validatePasswordSuccessForFIPS() throws Throwable {
        rjr.then(HudsonPrivateSecurityRealmFIPSTest::validatePasswordSuccessForFIPSStep);
    }

    private static void validatePasswordSuccessForFIPSStep(JenkinsRule j) throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        User u1 =  securityRealm.createAccount("test", "aValidFipsPass");

        WebClient wc = j.createWebClient();
        wc.login("test", "aValidFipsPass");

        HtmlPage configurePage = wc.goTo(u1.getUrl() + "/security/");
        HtmlPasswordInput password1 = configurePage.getElementByName("user.password");
        HtmlPasswordInput password2 = configurePage.getElementByName("user.password2");
        //should pass as the passwords are same and length >=14 In FIPS mode.
        password1.setText("14charPassword");
        password2.setText("14charPassword");

        HtmlForm form = configurePage.getFormByName("config");
        HtmlPage success = j.submit(form);
        assertThat(success.getWebResponse().getStatusCode(), is(200));
    }

    private static Matcher<LogRecord> incorrectHashingLogEntry() {
        return Matchers.hasProperty("message",
                is("A password appears to be stored (or is attempting to be stored) that was created with a different hashing/encryption algorithm, check the FIPS-140 state of the system has not changed inadvertently"));
    }

    private static List<LogRecord> getLogRecords() {
        return Jenkins.get().getLog().getLogRecorder(LOG_RECORDER_NAME).getLogRecords();
    }

    private static void setupLogRecorder() {
        LogRecorderManager lrm = Jenkins.get().getLog();
        LogRecorder lr = new LogRecorder(LOG_RECORDER_NAME);
        LogRecorder.Target target = new LogRecorder.Target(HudsonPrivateSecurityRealm.class.getName(), Level.WARNING);
        lr.setLoggers(List.of(target));

        lrm.getRecorders().add(lr);
    }
}
