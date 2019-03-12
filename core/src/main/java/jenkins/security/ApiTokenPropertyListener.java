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

package jenkins.security;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.UserProperty;
import hudson.model.UserPropertyListener;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Listener notified of user api-token creation and deletion events from the jenkins UI.
 */
@Extension
public class ApiTokenPropertyListener implements UserPropertyListener {

    /**
     * @since TODO
     *
     * Fired when an api token has been created.
     *
     * @param username the user
     * @param value api token property of the user
     *
     * **/
    static void fireOnCreated(@Nonnull String username, @Nonnull UserProperty value) {
        if (value instanceof ApiTokenProperty) {
            for (ApiTokenPropertyListener l : all()) {
                l.onCreated(username, value);
            }
        }
    }

    /**
     * @since TODO
     *
     * Fired when an api token has been revoked.
     *
     * @param username the user
     * @param value api token property of the user
     *
     * **/
    static void fireOnDeleted(@Nonnull String username, @Nonnull UserProperty value) {
        if (value instanceof ApiTokenProperty) {
            for (ApiTokenPropertyListener l : all()) {
                l.onDeleted(username, value);
            }
        }
    }

    static List<ApiTokenPropertyListener> all() { return ExtensionList.lookup(ApiTokenPropertyListener.class); }
}
