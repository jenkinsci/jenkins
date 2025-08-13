/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package jenkins.security.seed;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.User;
import java.util.List;
import jenkins.util.Listeners;

/**
 * Listener notified when a user was requested to changed their seed
 * @since 2.160 and 2.150.2, but restricted (unavailable to plugins) before 2.406
 */
public abstract class UserSeedChangeListener implements ExtensionPoint {

    /**
     * Called after a seed was changed but before the user is saved.
     * @param user The target user
     */
    public abstract void onUserSeedRenewed(@NonNull User user);

    /**
     * Will notify all the registered listeners about the event
     * @param user The target user
     */
    public static void fireUserSeedRenewed(@NonNull User user) {
        Listeners.notify(UserSeedChangeListener.class, true, l -> l.onUserSeedRenewed(user));
    }

    private static List<UserSeedChangeListener> all() {
        return ExtensionList.lookup(UserSeedChangeListener.class);
    }
}
