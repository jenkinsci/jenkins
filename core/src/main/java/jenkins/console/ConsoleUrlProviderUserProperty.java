/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

package jenkins.console;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import java.util.List;

import hudson.model.userproperty.UserPropertyCategory;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Allows users to activate and sort {@link ConsoleUrlProvider} extensions based on their preferences.
 * @see ConsoleUrlProviderGlobalConfiguration
 * @since 2.433
 */
@Restricted(NoExternalUse.class)
public class ConsoleUrlProviderUserProperty extends UserProperty {
    private List<ConsoleUrlProvider> providers;

    @DataBoundConstructor
    public ConsoleUrlProviderUserProperty() { }

    public @CheckForNull List<ConsoleUrlProvider> getProviders() {
        return providers;
    }

    @DataBoundSetter
    public void setProviders(List<ConsoleUrlProvider> providers) {
        this.providers = providers;
    }

    @Extension
    @Symbol("consoleUrlProvider")
    public static class DescriptorImpl extends UserPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.consoleUrlProviderDisplayName();
        }

        @Override
        public String getDescription() {
            return Messages.consoleUrlProviderDisplayName_Description();
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Appearance.class);
        }

        @Override
        public UserProperty newInstance(User user) {
            return new ConsoleUrlProviderUserProperty();
        }

        @Override
        public boolean isEnabled() {
            return ConsoleUrlProvider.isEnabled();
        }
    }
}
