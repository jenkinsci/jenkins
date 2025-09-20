/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.security.HudsonPrivateSecurityRealm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Tests for {@link UserDetailsCache}.
 */
@WithJenkins
class UserDetailsCacheTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("alice", "veeerysecret");
    }

    @Test
    void getCachedTrue() throws Exception {
        UserDetailsCache cache = UserDetailsCache.get();
        assertNotNull(cache);
        UserDetails alice = cache.loadUserByUsername("alice");
        assertNotNull(alice);
        UserDetails alice1 = cache.getCached("alice");
        assertNotNull(alice1);
    }

    @Test
    void getCachedFalse() {
        UserDetailsCache cache = UserDetailsCache.get();
        assertNotNull(cache);
        UserDetails alice1 = cache.getCached("alice");
        assertNull(alice1);
    }

    @Test
    void getCachedTrueNotFound() {

        UserDetailsCache cache = UserDetailsCache.get();
        assertNotNull(cache);
        assertThrows(UsernameNotFoundException.class, () -> cache.loadUserByUsername("bob"));
        assertThrows(UsernameNotFoundException.class, () -> cache.getCached("bob"));
    }

    @Test
    void getCachedFalseNotFound() {
        UserDetailsCache cache = UserDetailsCache.get();
        assertNotNull(cache);
        UserDetails bob = cache.getCached("bob");
        assertNull(bob);
    }

}
