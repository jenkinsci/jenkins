package hudson.tasks;

import java.util.regex.Matcher;

import junit.framework.TestCase;

public class UserAvatarResolverTest extends TestCase {

    public void testThatDefaultImageIsReturnedIfRegexFails() {
        String avatar = UserAvatarResolver.resolve(null, "meh");
        assertEquals("/images/meh/user.png", avatar);
    }

    public void testIconSizeRegex() {
        Matcher matcher = UserAvatarResolver.iconSizeRegex.matcher("12x15");
        assertTrue(matcher.matches());
        assertEquals("12", matcher.group(1));
        assertEquals("15", matcher.group(2));
    }
}
