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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;

import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm.Details;
import java.util.logging.Level;
import org.hamcrest.Matcher;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.LoggerRule;


@For(HudsonPrivateSecurityRealm.class)
public class HudsonPrivateSecurityRealmFIPSTest {

    // the jbcrypt encoded for of "a" without the quotes
    private static final String JBCRYPT_ENCODED_PASSWORD = "#jbcrypt:$2a$06$m0CrhHm10qJ3lXRY.5zDGO3rS2KdeeWLuGmsfGlMfOxih58VYVfxe";

    @ClassRule
    // do not use the FIPS140 class here as that initializes the field before we set the property!
    public static TestRule flagRule = FlagRule.systemProperty("jenkins.security.FIPS140.COMPLIANCE", "true");

    @Rule
    public LoggerRule lr = new LoggerRule().record(HudsonPrivateSecurityRealm.class, Level.WARNING).capture(5);

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void generalLogin() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        User u1 = securityRealm.createAccount("user", "password");
        u1.setFullName("A User");
        u1.save();

        // we should be using PBKDF2 hasher
        String hashedPassword = u1.getProperty(Details.class).getPassword();
        assertThat(hashedPassword, startsWith("$PBKDF2$HMACSHA512:210000:"));

        WebClient wc = j.createWebClient();
        wc.login("user", "password");

        assertThrows(FailingHttpStatusCodeException.class, () -> wc.login("user", "wrongPass"));
    }

    @Test
    public void userCreationWithHashedPasswords() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        // "password" after it has gone through the KDF
        securityRealm.createAccountWithHashedPassword("user_hashed",
                "$PBKDF2$HMACSHA512:210000:ffbb207b847010af98cdd2b09c79392c$f67c3b985daf60db83a9088bc2439f7b77016d26c1439a9877c4f863c377272283ce346edda4578a5607ea620a4beb662d853b800f373297e6f596af797743a6");
        WebClient wc = j.createWebClient();

        // login should succeed
        wc.login("user_hashed", "password");

        assertThrows(FailingHttpStatusCodeException.class, () -> wc.login("user_hashed", "password2"));
        assertThat(lr, not(hasIncorrectHashingLogEntry()));
    }

    @Test
    public void userLoginAfterEnablingFIPS() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        User u1 = securityRealm.createAccount("user", "a");
        u1.setFullName("A User");
        // overwrite the password property using an password created using an incorrect algorithm
        u1.addProperty(Details.fromHashedPassword(JBCRYPT_ENCODED_PASSWORD));

        u1.save();
        assertThat(u1.getProperty(Details.class).getPassword(), is(JBCRYPT_ENCODED_PASSWORD));

        try (WebClient wc = j.createWebClient()) {
            assertThrows(FailingHttpStatusCodeException.class, () -> wc.login("user", "a"));
        }
        assertThat(lr, hasIncorrectHashingLogEntry());
    }

    @Test
    public void userCreationWithJBCryptPasswords() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                () -> securityRealm.createAccountWithHashedPassword("user_hashed_incorrect_algorithm", JBCRYPT_ENCODED_PASSWORD));
        assertThat(illegalArgumentException.getMessage(),
                is("The hashed password was hashed with an incorrect algorithm. Jenkins is expecting $PBKDF2"));
    }

    private static Matcher<LoggerRule> hasIncorrectHashingLogEntry() {
        return LoggerRule.recorded(is(
                "A password appears to be stored (or is attempting to be stored) that was created with a different hashing/encryption algorithm, check the FIPS-140 state of the system has not changed inadvertently"));
    }
}
