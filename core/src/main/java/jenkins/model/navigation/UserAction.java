/*
 * The MIT License
 *
 * Copyright (c) 2025, Jan Faracik
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

package jenkins.model.navigation;

import static hudson.Functions.getAvatar;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.RootAction;
import hudson.model.User;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Display the user avatar in the navigation bar.
 * Provides a handy jumplist for common user actions.
 */
@Extension(ordinal = -1)
public class UserAction implements RootAction {

    @Override
    public String getIconFileName() {
        User current = User.current();

        if (current == null) {
            return null;
        }

        return getAvatar(current, "96x96");
    }

    @Override
    public String getDisplayName() {
        User current = User.current();

        if (current == null) {
            return null;
        }

        return current.getFullName();
    }

    @Override
    public String getUrlName() {
        User current = User.current();

        if (current == null) {
            return null;
        }

        return current.getUrl();
    }

    @Restricted(NoExternalUse.class)
    public User getUser() {
        return User.current();
    }

    @Override
    public boolean isPrimaryAction() {
        return true;
    }

    @Restricted(NoExternalUse.class)
    public List<Action> getActions() {
        User current = User.current();

        if (User.current() == null) {
            return null;
        }

        List<Action> actions = new ArrayList<>();
        actions.addAll(current.getPropertyActions());
        actions.addAll(current.getTransientActions());
        return actions.stream().filter(e -> e.getIconFileName() != null).toList();
    }
}
