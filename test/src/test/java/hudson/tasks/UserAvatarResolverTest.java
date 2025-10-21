/*
 * The MIT License
 *
 * Copyright (c) 2011, Erik Ramfelt
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

package hudson.tasks;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Extension;
import hudson.model.User;
import java.util.regex.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class UserAvatarResolverTest {

    private static User expUser;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void defaultImageIsReturnedIfRegexFails() {
        String avatar = UserAvatarResolver.resolve(User.getOrCreateByIdOrFullName("USER"), "meh");
        assertThat(avatar, endsWith("symbol-person-circle"));
    }

    @Test
    void resolverIsUsed() {
        expUser = User.getOrCreateByIdOrFullName("unique-user-not-used-in-anyother-test");
        String avatar = UserAvatarResolver.resolve(expUser, "20x20");
        assertEquals("http://myown.image", avatar);
    }

    @Test
    void noResolverCanFindAvatar() {
        String avatar = UserAvatarResolver.resolve(User.getOrCreateByIdOrFullName("USER"), "20x20");
        assertThat(avatar, endsWith("symbol-person-circle"));
    }

    @Test
    @WithoutJenkins
    void iconSizeRegex() {
        Matcher matcher = UserAvatarResolver.iconSizeRegex.matcher("12x15");
        assertTrue(matcher.matches());
        assertEquals("12", matcher.group(1));
        assertEquals("15", matcher.group(2));
    }

    @Extension
    public static final class UserAvatarResolverImpl extends UserAvatarResolver {
        @Override
        public String findAvatarFor(User u, int width, int height) {
            if (u != null && u == expUser) {
                return "http://myown.image";
            }
            return null;
        }
    }
}
