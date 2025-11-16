/*
 * The MIT License
 *
 * Copyright (c) 2025, Markus Winter
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

package jenkins.users;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * User property to control the visibility of the side panel in the user interface of various Jenkins pages.
 */
public class SidePanelUserProperty extends UserProperty {

    private boolean showNodesSidePanel;

    private boolean showLoadstatsSidePanel;

    @DataBoundConstructor
    public SidePanelUserProperty() {
        this.showNodesSidePanel = false;
    }

    public boolean isShowNodesSidePanel() {
        return showNodesSidePanel;
    }

    @DataBoundSetter
    public void setShowNodesSidePanel(boolean showNodesSidePanel) {
        this.showNodesSidePanel = showNodesSidePanel;
    }

    public boolean isShowLoadstatsSidePanel() {
        return showLoadstatsSidePanel;
    }

    @DataBoundSetter
    public void setShowLoadstatsSidePanel(boolean showLoadstatsSidePanel) {
        this.showLoadstatsSidePanel = showLoadstatsSidePanel;
    }

    @Extension
    @Symbol("SidePanel")
    public static class DescriptorImpl extends UserPropertyDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SidePanelUserProperty_DisplayName();
        }

        @Override
        public UserProperty newInstance(User user) {
            return new SidePanelUserProperty();
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Preferences.class);
        }
    }
}
