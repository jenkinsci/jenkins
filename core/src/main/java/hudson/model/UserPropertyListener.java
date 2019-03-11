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

package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.UserProperty;
import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * Listener interface which all other user property-specific event-listeners make use of.
 */
public interface UserPropertyListener extends ExtensionPoint {

    static final Logger LOGGER = Logger.getLogger(UserPropertyListener.class.getName());

    /**
     * Fired when a new user property has been created.
     *
     * @param username the user
     * @param value property that was newly created.
     *
     */
    default void onCreated(@Nonnull String username, @Nonnull UserProperty value) {
        LOGGER.log(Level.FINE, MessageFormat.format("new {0} property created for user {1}", value.getClass().toString(), username));
    }

    /**
     * Fired when a new user property has been created.
     *
     * @param username the user
     * @param value property that was newly created.
     *
     */
    default void onCreated(@Nonnull String username, @Nonnull Object value) {
        LOGGER.log(Level.FINE, MessageFormat.format("new {0} property created for user {1}", value.toString(), username));
    }

    /**
     * Fired when an existing user property has been changed.
     *
     * @param username the user
     * @param oldValue old property of the user
     * @param newValue new property of the user
     *
     */
    default void onChanged(@Nonnull String username, @Nonnull UserProperty oldValue, @Nonnull UserProperty newValue) {
        LOGGER.log(Level.FINE, MessageFormat.format("{0} property changed for user {1}", oldValue.getClass().toString(), username));
    }

    /**
     * Fired when an existing user property has been changed.
     *
     * @param username the user
     * @param oldValue old property of the user
     * @param newValue new property of the user
     *
     */
    default void onChanged(@Nonnull String username, @Nonnull Object oldValue, @Nonnull Object newValue) {
        LOGGER.log(Level.FINE, MessageFormat.format("{0} property changed for user {1}", oldValue.toString(), username));
    }

    /**
     * Fired when an existing user property has been removed or deleted.
     *
     * @param username the user
     * @param value property that was removed.
     *
     */
    default void onDeleted(@Nonnull String username, @Nonnull UserProperty value) {
        LOGGER.log(Level.FINE, MessageFormat.format("new {0} property created for user {1}", value.getClass().toString(), username));
    }

    /**
     * Fired when an existing user property has been removed or deleted.
     *
     * @param username the user
     * @param value property that was removed
     *
     */
    default void onDeleted(@Nonnull String username, @Nonnull Object value) {
        LOGGER.log(Level.FINE, MessageFormat.format("new {0} property created for user {1}", value.toString(), username));
    }

    static List<UserPropertyListener> all() { return ExtensionList.lookup(UserPropertyListener.class); }
}
