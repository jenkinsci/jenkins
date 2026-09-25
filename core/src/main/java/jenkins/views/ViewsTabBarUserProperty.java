/*
 * The MIT License
 *
 * Copyright (c) 2025 Markus Winter
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

package jenkins.views;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import hudson.views.ViewsTabBar;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Restricted(NoExternalUse.class)
public class ViewsTabBarUserProperty extends UserProperty {

    private ViewsTabBar viewsTabBar;

    @DataBoundConstructor
    public ViewsTabBarUserProperty() {
    }

    @DataBoundSetter
    public void setViewsTabBar(ViewsTabBar viewsTabBar) {
        this.viewsTabBar = viewsTabBar;
    }

    @CheckForNull
    public ViewsTabBar getViewsTabBar() {
        return viewsTabBar;
    }

    @Extension
    @Symbol("viewsTabBar")
    public static class DescriptorImpl extends UserPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ViewsTabBarUserProperty_DisplayName();
        }

        @Override
        public UserProperty newInstance(User user) {
            return new ViewsTabBarUserProperty();
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Preferences.class);
        }
    }
}
