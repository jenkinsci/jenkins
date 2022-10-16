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
import hudson.ExtensionPoint;
import hudson.model.Messages;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.Map;

//TODO DRAFT experimental to split
/**
 * Property of {@link User} responsible to store their preferences.
 *
 * TODO rest of the javadoc
 * 
 * @since TODO
 */
@ExportedBean
public class UserPreferencesProperty extends UserProperty {

    private Map<UserPreferenceKey, UserPreferenceValue> preferences;
    
    
    
    // descriptor must be of the UserPropertyDescriptor type
    @Override
    public UserPropertyDescriptor getDescriptor() {
        return (UserPropertyDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }

    public static class UserPreference implements ExtensionPoint {
        public UserPreferenceKey getKey(){
            return null;
        }
        public UserPreferenceValue getValue(){
            return null;
        }
    }

    public static class UserPreferenceKey {

    }

    public static class UserPreferenceValue {

    }

//    @Extension 
    @Symbol("preferences")
    public static class DescriptorImpl extends UserPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.UserPreferencesProperty_DisplayName();
        }

        @Override
        public UserProperty newInstance(User user) {
            return new UserPreferencesProperty();
        }
    }
}
