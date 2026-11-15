/*
 * The MIT License
 *
 * Copyright (c) 2026 Manthan Sharma
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

package hudson.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import hudson.model.User;
import hudson.model.UserProperty;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.StaplerRequest2;
import org.mockito.MockedStatic;

class UserSearchPropertyTest {

    @Test
    void isCaseInsensitiveWhenUserIsNullReturnsDefaultTrue() {
        try (MockedStatic<User> mockedUser = mockStatic(User.class)) {
            mockedUser.when(User::current).thenReturn(null);
            assertTrue(UserSearchProperty.isCaseInsensitive());
        }
    }

    @Test
    @Issue("JENKINS-27770")
    void isCaseInsensitiveWhenUserHasNoPropertyReturnsDefaultTrue() {
        try (MockedStatic<User> mockedUser = mockStatic(User.class)) {
            User user = mock(User.class);
            when(user.getProperty(UserSearchProperty.class)).thenReturn(null);
            mockedUser.when(User::current).thenReturn(user);

            assertTrue(UserSearchProperty.isCaseInsensitive());
        }
    }

    @Test
    void isCaseInsensitiveWhenUserHasPropertyReturnsConfiguredValue() {
        try (MockedStatic<User> mockedUser = mockStatic(User.class)) {
            User user = mock(User.class);
            when(user.getProperty(UserSearchProperty.class)).thenReturn(new UserSearchProperty(false));
            mockedUser.when(User::current).thenReturn(user);

            assertFalse(UserSearchProperty.isCaseInsensitive());

            when(user.getProperty(UserSearchProperty.class)).thenReturn(new UserSearchProperty(true));
            assertTrue(UserSearchProperty.isCaseInsensitive());
        }
    }

    @Test
    void newInstanceDefaultsToTrue() {
        UserSearchProperty.DescriptorImpl descriptor = new UserSearchProperty.DescriptorImpl();
        UserProperty property = descriptor.newInstance((User) null);
        assertTrue(property instanceof UserSearchProperty);
        assertTrue(((UserSearchProperty) property).getInsensitiveSearch());
    }

    @Test
    void newInstanceFromJson() throws Exception {
        UserSearchProperty.DescriptorImpl descriptor = new UserSearchProperty.DescriptorImpl();
        JSONObject json = new JSONObject();
        json.put("insensitiveSearch", false);

        UserProperty property = descriptor.newInstance((StaplerRequest2) null, json);
        assertTrue(property instanceof UserSearchProperty);
        assertFalse(((UserSearchProperty) property).getInsensitiveSearch());
    }
}
