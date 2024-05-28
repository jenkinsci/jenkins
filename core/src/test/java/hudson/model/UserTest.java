package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/*
 * The MIT License
 *
 * Copyright (c) 2016 Oleg Nenashev.
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

/**
 * Unit tests for the {@link User} class.
 * @author Oleg Nenashev
 */
public class UserTest {

    @Test
    @Issue("JENKINS-33600")
    public void blankIdsOrFullNamesShouldNotBeAllowed() {
        assertThat("Null user IDs should not be allowed", User.isIdOrFullnameAllowed(null), is(false));
        assertThat("Empty user IDs should not be allowed", User.isIdOrFullnameAllowed(""), is(false));
        assertThat("Blank user IDs should not be allowed", User.isIdOrFullnameAllowed("      "), is(false));
    }

    @Test
    @Issue("JENKINS-35967")
    public void shouldNotAllowIllegalRestrictedNamesInWrongCase() {
        assertIdOrFullNameNotAllowed("system");
        assertIdOrFullNameNotAllowed("System");
        assertIdOrFullNameNotAllowed("SYSTEM");
        assertIdOrFullNameNotAllowed("syStem");
        assertIdOrFullNameNotAllowed("sYstEm");
    }

    @Test
    @Issue("JENKINS-35967")
    public void shouldNotAllowIllegalRestrictedNamesEvenIfTrimmed() {
        for (String username : User.getIllegalPersistedUsernames()) {
            assertIdOrFullNameNotAllowed(username);
            assertIdOrFullNameNotAllowed(" " + username);
            assertIdOrFullNameNotAllowed(username + " ");
            assertIdOrFullNameNotAllowed("      " + username + "    ");
            assertIdOrFullNameNotAllowed("\t" + username + "\t");
        }
    }

    private void assertIdOrFullNameNotAllowed(String id) {
        assertThat("User ID or full name '" + id + "' should not be allowed",
                User.isIdOrFullnameAllowed(id), is(false));
    }

    // Test If username is valid or not
    @Test
    public void testGetUserInvalidName() throws Exception {
        {
            User user = User.get("%xJU",false,Collections.emptyMap());
            assertNull("User %xJU should not be created.", user);
        }
        j.jenkins.reload();
        {
            User user2 = User.get("John Smith");
            user2.setFullName("Alice Smith");
            assertEquals("Users should have same id even after editing the name", "John Smith", user2.getId());
            User user4 = User.get("Marie", false, Collections.EMPTY_MAP);
            assertNull("User should not be created because Marie does not exists.", user4);
        }
    }

    // Test user and email format is valid or not
    @Test
    public void testGetAndGetAllForEmail() {
        {
            User user = User.get("john.smith@test.org", false, Collections.emptyMap());
            assertNull("User john.smith@test.org should not be created.", user);
            assertFalse("Jenkins should not contain user john.smith@test.org.", User.getAll().contains(user));
        }
        {
            User user2 = User.get("john.rambo.smith@test.org", true, Collections.emptyMap());
            assertNotNull("User with email john.rambo.smith@test.org should be created.", user2);
            assertTrue("Jenkins should contain user john.rambo.smith@test.org", User.getAll().contains(user2));
        }
        {
            // checking if it can get the existing user
            User user = User.get("john.rambo.smith@test.org", false, Collections.emptyMap());
            assertNotNull("User with email john.rambo.smith@test.org should be created.", user);
            assertTrue("Jenkins should contain user john.rambo.smith@test.org", User.getAll().contains(user));
        }

    }
}
