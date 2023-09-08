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
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThrows;

import hudson.model.User;
import hudson.security.HudsonPrivateSecurityRealm.Details;
import org.htmlunit.FailingHttpStatusCodeException;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;


@For(HudsonPrivateSecurityRealm.class)
public class HudsonPrivateSecurityRealmFIPSTest {

    @ClassRule
    // do not use the FIPS140 class here as that initializes the field before we set the property!
    public static TestRule flagRule = FlagRule.systemProperty("jenkins.security.FIPS140.COMPLIANCE", "true");

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
                "$PBKDF2$HMACSHA512:1000:89901f42cb107a8dcb2e1ab5e6891aaa$2983a219a5f35115b35d2eb8d752498414628ea8428d97f707bdf5aaa9e485d16e4a3b9e36e55115735924f4be4cb48f560b3da333e11fd250fd15362bef315d");
        WebClient wc = j.createWebClient();

        // login should succeed
        wc.login("user_hashed", "password");

        assertThrows(FailingHttpStatusCodeException.class, () -> wc.login("user_hashed", "password2"));
    }
}
