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

package jenkins.model.experimentalflags;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import java.util.HashMap;
import java.util.Map;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;


/**
 * Per user experimental flags to enable features that still not completely ready to be active by default.
 *
 * @since 2.395
 */
public class UserExperimentalFlagsProperty extends UserProperty {
    private Map<String, String> flags = new HashMap<>();

    @DataBoundConstructor
    public UserExperimentalFlagsProperty() {
    }

    public UserExperimentalFlagsProperty(Map<String, String> flags) {
        this.flags = new HashMap<>(flags);
    }

    public @CheckForNull Object getFlagValue(String flagKey) {
        return this.flags.get(flagKey);
    }

    @Extension(ordinal = -500)
    @Symbol("experimentalFlags")
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        @Override
        public @NonNull String getDisplayName() {
            return Messages.UserExperimentalFlagsProperty_DisplayName();
        }

        @Override
        public @NonNull UserProperty newInstance(User user) {
            return new UserExperimentalFlagsProperty();
        }

        @Override
        public UserProperty newInstance(@Nullable StaplerRequest2 req, @NonNull JSONObject formData) throws FormException {
            JSONObject flagsObj = formData.getJSONObject("flags");
            Map<String, String> flags = new HashMap<>();
            for (String key : flagsObj.keySet()) {
                String value = (String) flagsObj.get(key);
                if (!value.isEmpty()) {
                    flags.put(key, value);
                }
            }
            return new UserExperimentalFlagsProperty(flags);
        }

        @NonNull
        @Override
        public UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Experimental.class);
        }
    }
}
