/*
 * The MIT License
 *
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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.UserPropertyListener;
import java.util.List;
import javax.annotation.Nonnull;

import static hudson.security.HudsonPrivateSecurityRealm.PASSWORD_ENCODER;

/**
 * Listener notified of user password change events from the jenkins UI.
 */
@Extension
public class PasswordPropertyListener implements UserPropertyListener {

    /**
     * @since TODO
     *
     * Fired when an existing user password property has been changed.
     *
     * @param username the user
     * @param oldValue old password of the user
     * @param newValue new password of the user
     *
     * **/
    static void fireOnChanged(@Nonnull String username, @Nonnull String oldValue, @Nonnull String newValue) {
        if ((!oldValue.equals(newValue)) && (!PASSWORD_ENCODER.isPasswordValid(oldValue, newValue, null))) {
            for (PasswordPropertyListener l : all()) {
                l.onChanged(username, oldValue, newValue);
            }
        }
    }

    static List<PasswordPropertyListener> all() { return ExtensionList.lookup(PasswordPropertyListener.class); }
}
