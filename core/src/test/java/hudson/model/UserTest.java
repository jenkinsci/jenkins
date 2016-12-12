package hudson.model;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
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
    public void shoudNotAllowIllegalRestrictedNamesInWrongCase() {
        assertIdOrFullNameNotAllowed("system");
        assertIdOrFullNameNotAllowed("System");
        assertIdOrFullNameNotAllowed("SYSTEM");
        assertIdOrFullNameNotAllowed("syStem");
        assertIdOrFullNameNotAllowed("sYstEm");
    }
    
    @Test
    @Issue("JENKINS-35967")
    public void shoudNotAllowIllegalRestrictedNamesEvenIfTrimmed() {
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
    
}
