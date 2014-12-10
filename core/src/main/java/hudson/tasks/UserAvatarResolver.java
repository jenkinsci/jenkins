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

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.model.User;
import javax.annotation.CheckForNull;

/**
 * Infers avatar image URLs for users
 * 
 * <p>
 * This is an extension point of Jenkins. Plugins that contribute a new implementation
 * of this class should put {@link Extension} on your implementation class, like this:
 *
 * <pre>
 * &#64;Extension
 * class MyUserAvatarResolver extends {@link UserAvatarResolver} {
 *   ...
 * }
 * </pre>
 *
 * @author Erik Ramfelt
 * @since 1.434
 */
public abstract class UserAvatarResolver implements ExtensionPoint {

    /** Regex pattern for splitting up the icon size string that is used in jelly pages. */
    static Pattern iconSizeRegex = Pattern.compile("(\\d+)x(\\d+)");
    
    /**
     * Finds an avatar image URL string for a user.
     *
     * <p>
     * This method is called when a web page is going to show an avatar for a {@link User}.
     *
     * <p>
     * When multiple resolvers are installed, they are consulted in order and
     * the search will be over when an avatar is found by someone.
     *
     * <p>
     * Since {@link UserAvatarResolver} is singleton, this method can be invoked concurrently
     * from multiple threads.
     * @param u the user
     * @param width the preferred width of the avatar
     * @param height the preferred height of the avatar.
     * 
     * @return null if the inference failed.
     */
    public abstract String findAvatarFor(User u, int width, int height);
    
    /**
     * Resolve an avatar image URL string for the user.
     * Note that this method must be called from an HTTP request to be reliable; else use {@link #resolveOrNull}.
     * @param u user
     * @param avatarSize the preferred image size, "[width]x[height]"
     * @return a URL string for a user Avatar image.
     */
    public static String resolve(User u, String avatarSize) {
        String avatar = resolveOrNull(u, avatarSize);
        return avatar != null ? avatar : Jenkins.getInstance().getRootUrl() + Functions.getResourcePath() + "/images/" + avatarSize + "/user.png";
    }

    /**
     * Like {@link #resolve} but returns null rather than a fallback URL in case there is no special avatar.
     * @since 1.518
     */
    public static @CheckForNull String resolveOrNull(User u, String avatarSize) {
        Matcher matcher = iconSizeRegex.matcher(avatarSize);
        if (matcher.matches() && matcher.groupCount() == 2) {
            int width = Integer.parseInt(matcher.group(1));
            int height = Integer.parseInt(matcher.group(2));
            for (UserAvatarResolver r : all()) {
                String name = r.findAvatarFor(u, width, height);
                if(name!=null) return name;
            }
        } else {
            LOGGER.warning(String.format("Could not split up the avatar size (%s) into a width and height.", avatarSize));
        }

        return null;
    }

    /**
     * Returns all the registered {@link UserAvatarResolver} descriptors.
     */
    public static ExtensionList<UserAvatarResolver> all() {
        return ExtensionList.lookup(UserAvatarResolver.class);
    }

    private static final Logger LOGGER = Logger.getLogger(UserAvatarResolver.class.getName());
}
