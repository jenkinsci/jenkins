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

import hudson.ExtensionList;
import hudson.model.User;
import jenkins.security.SecurityListener;
import org.apache.tools.ant.ExtensionPoint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener notified when a user was requested to changed their seed
 */
//TODO remove restriction on the weekly after the security fix
@Restricted(NoExternalUse.class)
public abstract class UserSeedChangeListener extends ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(SecurityListener.class.getName());

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
        for (UserSeedChangeListener l : all()) {
            try {
                l.onUserSeedRenewed(user);
            }
            catch (Exception e) {
                LOGGER.log(Level.WARNING, "Exception caught during onUserSeedRenewed event", e);
            }
        }
    }

    private static List<UserSeedChangeListener> all() {
        return ExtensionList.lookup(UserSeedChangeListener.class);
    }
}
