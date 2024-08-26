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
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.ModelObject;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Grouping of related {@link UserProperty}s.
 *
 * <p>
 * To facilitate the separation of the user properties into multiple pages, tabs, and so on,
 * {@link UserProperty}s are classified into categories (such as "security", "preferences", as well
 * as the catch-all "unclassified".) Categories themselves are extensible &mdash; plugins may introduce
 * its own category as well, although that should only happen if you are creating a big enough subsystem.
 *
 * @since 2.468
 * @see UserProperty
 */
public abstract class UserPropertyCategory implements ExtensionPoint, ModelObject {
    /**
     * One-line plain text message that explains what this category is about.
     * This can be used in the UI to help the user pick the right category.
     *
     * The text should be longer than {@link #getDisplayName()}
     */
    public abstract String getShortDescription();

    /**
     * Returns all the registered {@link UserPropertyCategory} descriptors.
     */
    public static ExtensionList<UserPropertyCategory> all() {
        return ExtensionList.lookup(UserPropertyCategory.class);
    }

    public static @NonNull <T extends UserPropertyCategory> T get(Class<T> type) {
        T category = all().get(type);
        if (category == null) {
            throw new AssertionError("Category not found. It seems the " + type + " is not annotated with @Extension and so not registered");
        }
        return category;
    }

    /**
     * This category is used when the {@link hudson.model.UserPropertyDescriptor} has not implemented
     * the {@link UserPropertyDescriptor#getUserPropertyCategory()} method
     * (or the getUserPropertyCategoryAsString method for compatibility reason).
     * <p>
     * If you do not know what to use, choose the {@link Account} instead of this one.
     */
    @Extension
    @Symbol("unclassified")
    @Restricted(DoNotUse.class)
    public static class Unclassified extends UserPropertyCategory {
        @Override
        public String getDisplayName() {
            return Messages.UserPropertyCategory_Unclassified_DisplayName();
        }

        @Override
        public String getShortDescription() {
            return Messages.UserPropertyCategory_Unclassified_ShortDescription();
        }
    }

    /**
     * User property related to account settings (e.g. timezone, email, ...).
     * <p>
     * It could be seen as the default choice for {@link UserProperty} that are defining their category.
     * Currently it has the same effect as {@link Unclassified} but the behavior could change in the future.
     */
    @Extension
    @Symbol("account")
    public static class Account extends UserPropertyCategory {
        @Override
        public String getDisplayName() {
            return Messages.UserPropertyCategory_Account_DisplayName();
        }

        @Override
        public String getShortDescription() {
            return Messages.UserPropertyCategory_Account_ShortDescription();
        }
    }

    /**
     * Preferences related configurations (e.g. notification type, default view, ...).
     */
    @Extension
    @Symbol("preferences")
    public static class Preferences extends UserPropertyCategory {
        @Override
        public String getDisplayName() {
            return Messages.UserPropertyCategory_Preferences_DisplayName();
        }

        @Override
        public String getShortDescription() {
            return Messages.UserPropertyCategory_Preferences_ShortDescription();
        }
    }

    /**
     * Per user feature flags (e.g. new design, ...).
     */
    @Extension
    @Symbol("experimental")
    public static class Experimental extends UserPropertyCategory {
        @Override
        public String getDisplayName() {
            return Messages.UserPropertyCategory_Experimental_DisplayName();
        }

        @Override
        public String getShortDescription() {
            return Messages.UserPropertyCategory_Experimental_ShortDescription();
        }
    }

    /**
     * User interface related configurations (e.g. theme, language, ...).
     * <p>
     * See also {@link jenkins.appearance.AppearanceCategory}.
     */
    @Extension
    @Symbol("appearance")
    public static class Appearance extends UserPropertyCategory {
        @Override
        public String getDisplayName() {
            return Messages.UserPropertyCategory_Appearance_DisplayName();
        }

        @Override
        public String getShortDescription() {
            return Messages.UserPropertyCategory_Appearance_ShortDescription();
        }
    }


    /**
     * Security related configurations (e.g. API Token, SSH keys, ...).
     * With this separation, we can more easily add control on their modifications.
     */
    @Extension
    @Symbol("security")
    public static class Security extends UserPropertyCategory {
        @Override
        public String getDisplayName() {
            return Messages.UserPropertyCategory_Security_DisplayName();
        }

        @Override
        public String getShortDescription() {
            return Messages.UserPropertyCategory_Security_ShortDescription();
        }
    }

    /**
     * For user properties that are not expected to be displayed,
     * typically automatically configured by automated behavior, without direct user interaction.
     */
    @Extension
    @Symbol("invisible")
    public static class Invisible extends UserPropertyCategory {
        @Override
        public String getDisplayName() {
            return Messages.UserPropertyCategory_Invisible_DisplayName();
        }

        @Override
        public String getShortDescription() {
            return Messages.UserPropertyCategory_Invisible_ShortDescription();
        }
    }
}
