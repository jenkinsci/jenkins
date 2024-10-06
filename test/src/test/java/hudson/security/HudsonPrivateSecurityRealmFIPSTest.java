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
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import jenkins.model.Jenkins;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.RealJenkinsRule;


@For(HudsonPrivateSecurityRealm.class)
public class HudsonPrivateSecurityRealmFIPSTest {

    // the jbcrypt encoded for of "a" without the quotes
    private static final String JBCRYPT_ENCODED_PASSWORD = "#jbcrypt:$2a$06$m0CrhHm10qJ3lXRY.5zDGO3rS2KdeeWLuGmsfGlMfOxih58VYVfxe";

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

        User u1 = securityRealm.createAccount("user", "password");
        u1.setFullName("A User");
        u1.save();

        // we should be using PBKDF2 hasher
        String hashedPassword = u1.getProperty(Details.class).getPassword();
        assertThat(hashedPassword, startsWith("$PBKDF2$HMACSHA512:210000:"));

        try (WebClient wc = j.createWebClient()) {
            wc.login("user", "password");
            assertThrows(FailingHttpStatusCodeException.class, () -> wc.login("user", "wrongPass"));
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
        // "password" after it has gone through the KDF
        securityRealm.createAccountWithHashedPassword("user_hashed",
                "$PBKDF2$HMACSHA512:210000:ffbb207b847010af98cdd2b09c79392c$f67c3b985daf60db83a9088bc2439f7b77016d26c1439a9877c4f863c377272283ce346edda4578a5607ea620a4beb662d853b800f373297e6f596af797743a6");

        try (WebClient wc = j.createWebClient()) {
            // login should succeed
            wc.login("user_hashed", "password");
            assertThrows(FailingHttpStatusCodeException.class, () -> wc.login("user_hashed", "password2"));
        }
        assertThat(getLogRecords(), not(hasItem(incorrectHashingLogEntry())));
    }

    @Test
    public void userLoginAfterEnablingFIPS() throws Throwable {
        rjr.then(HudsonPrivateSecurityRealmFIPSTest::userLoginAfterEnablingFIPSStep);
    }

    private static void userLoginAfterEnablingFIPSStep(JenkinsRule j) throws Exception {
        setupLogRecorder();
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        User u1 = securityRealm.createAccount("user", "a");
        u1.setFullName("A User");
        // overwrite the password property using an password created using an incorrect algorithm
        Method m = Details.class.getDeclaredMethod("fromHashedPassword", String.class);
        m.setAccessible(true);
        Details d = (Details) m.invoke(null, JBCRYPT_ENCODED_PASSWORD);
        u1.addProperty(d);

        u1.save();
        assertThat(u1.getProperty(Details.class).getPassword(), is(JBCRYPT_ENCODED_PASSWORD));

        try (WebClient wc = j.createWebClient()) {
            assertThrows(FailingHttpStatusCodeException.class, () -> wc.login("user", "a"));
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
