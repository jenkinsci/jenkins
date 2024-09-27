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
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.User;

/**
 * User specific experimental flag to enable or disable specific behavior.
 * As it's user specific, usually this kind of feature flag is only used for UI.
 *
 * @since 2.395
 */
public abstract class UserExperimentalFlag<T> implements ExtensionPoint {
    private final String flagKey;

    protected UserExperimentalFlag(@NonNull String flagKey) {
        this.flagKey = flagKey;
    }

    public abstract @NonNull T getDefaultValue();

    /**
     * Convert the usable value into a serializable form that can be stored in the user property.
     * If no changes are necessary, simply returning the {@code rawValue} is fine.
     */
    public abstract @Nullable Object serializeValue(T rawValue);

    /**
     * Convert the serialized value into the usable instance.
     * If the instance is invalid (like after migration),
     * returning {@code null} will force to return the {@link #getDefaultValue()}
     */
    protected abstract @Nullable T deserializeValue(Object serializedValue);

    /**
     * The name that will be used in the configuration page for that flag
     * It must be user readable
     */
    public abstract String getDisplayName();

    /**
     * Describe what the flag is changing depending on its value.
     * This method is called in description.jelly, which could be overloaded by children.
     * It could return HTML content.
     */
    public abstract @Nullable String getShortDescription();

    /**
     * The ID used by the machine to link the flag with its value within the user properties
     */
    public @NonNull String getFlagKey() {
        return flagKey;
    }

    public @NonNull T getFlagValue() {
        User currentUser = User.current();
        if (currentUser == null) {
            // the anonymous user is not expected to use flags
            return this.getDefaultValue();
        }
        return this.getFlagValue(currentUser);
    }

    public @NonNull T getFlagValue(User user) {
        UserExperimentalFlagsProperty property = user.getProperty(UserExperimentalFlagsProperty.class);
        if (property == null) {
            // if for whatever reason there is no such property
            return this.getDefaultValue();
        }

        Object value = property.getFlagValue(this.flagKey);
        if (value == null) {
            return this.getDefaultValue();
        }

        T convertedValue = this.deserializeValue(value);
        if (convertedValue == null) {
            return this.getDefaultValue();
        }
        return convertedValue;
    }

    public String getFlagDescriptionPage() {
        return "flagDescription.jelly";
    }

    public String getFlagConfigPage() {
        return "flagConfig.jelly";
    }

    @NonNull
    @SuppressWarnings("rawtypes")
    public static ExtensionList<UserExperimentalFlag> all() {
        return ExtensionList.lookup(UserExperimentalFlag.class);
    }

    /**
     * From the flag class, return the value of the flag for the current user
     * If the returned value is {@code null},
     * it means that either the class was not found or the current user is anonymous
     */
    @SuppressWarnings("unchecked")
    public static @CheckForNull <T> T getFlagValueForCurrentUser(String flagClassCanonicalName) {
        Class<? extends UserExperimentalFlag<T>> flagClass;
        try {
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(flagClassCanonicalName);
            if (!UserExperimentalFlag.class.isAssignableFrom(clazz)) {
                return null;
            }
            flagClass = (Class<? extends UserExperimentalFlag<T>>) clazz;
        } catch (Exception e) {
            return null;
        }

        UserExperimentalFlag<T> userExperimentalFlag = all().get(flagClass);
        if (userExperimentalFlag == null) {
            return null;
        }

        return userExperimentalFlag.getFlagValue();
    }
}
