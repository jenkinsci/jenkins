/*
 * The MIT License
 *
 * Copyright (c) 2022, CloudBees, Inc.
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

package hudson.model.userproperty;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.TransientUserActionFactory;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class UserPropertyCategoryPreferencesAction extends UserPropertyCategoryAction implements Action {
    public UserPropertyCategoryPreferencesAction(@NonNull User user) {
        super(user);
    }

    @Override
    public String getDisplayName() {
        return Messages.UserPropertyCategoryPreferencesAction_DisplayName();
    }

    @Override
    public String getIconFileName() {
        return getTargetUser().hasPermission(Jenkins.ADMINISTER) ? "symbol-parameters" : null;
    }

    @Override
    public String getUrlName() {
        return "preferences";
    }

    public @NonNull List<UserPropertyDescriptor> getMyCategoryDescriptors() {
        return UserProperty.allByCategoryClass(UserPropertyCategory.Preferences.class);
    }

    /**
     * Inject the outer class configuration page into the sidenav and the request routing of the user
     */
    @Extension(ordinal = 300)
    @Symbol("preferences")
    public static class PreferencesActionFactory extends TransientUserActionFactory {
        public Collection<? extends Action> createFor(User target) {
            return Collections.singleton(new UserPropertyCategoryPreferencesAction(target));
        }
    }
}
