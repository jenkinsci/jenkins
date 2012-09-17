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

import hudson.Extension;
import hudson.model.User;

import java.util.regex.Matcher;

import org.jvnet.hudson.test.HudsonTestCase;

public class UserAvatarResolverTest extends HudsonTestCase {

    private static User expUser;
    
    public void testThatDefaultImageIsReturnedIfRegexFails() {
        String avatar = UserAvatarResolver.resolve(User.get("USER"), "meh");
        assertTrue(avatar.endsWith("/images/meh/user.png"));
    }
    
    public void testThatResolverIsUsed() {
        expUser = User.get("unique-user-not-used-in-anyother-test", true);
        String avatar = UserAvatarResolver.resolve(expUser, "20x20");
        assertEquals(avatar, "http://myown.image");
    }
    
    public void testThatNoResolverCanFindAvatar() {
        String avatar = UserAvatarResolver.resolve(User.get("USER"), "20x20");
        assertTrue(avatar.endsWith("/images/20x20/user.png"));
    }

    public void testIconSizeRegex() {
        Matcher matcher = UserAvatarResolver.iconSizeRegex.matcher("12x15");
        assertTrue(matcher.matches());
        assertEquals("12", matcher.group(1));
        assertEquals("15", matcher.group(2));
    }
    
    @Extension
    public static final class UserAvatarResolverImpl extends UserAvatarResolver {

        @Override
        public String findAvatarFor(User u, int width, int height) {
            if ((u != null) && (u == expUser)) {
                return "http://myown.image";
            }
            return null;
        }   
    }
}
