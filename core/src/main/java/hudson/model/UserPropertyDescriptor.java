/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer
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

package hudson.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.userproperty.UserPropertyCategory;
import java.util.Optional;
import org.jenkinsci.Symbol;

/**
 * {@link Descriptor} for {@link UserProperty}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class UserPropertyDescriptor extends Descriptor<UserProperty> {
    protected UserPropertyDescriptor(Class<? extends UserProperty> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link Describable} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.278
     */
    protected UserPropertyDescriptor() {
    }

    /**
     * Creates a default instance of {@link UserProperty} to be associated
     * with {@link User} object that wasn't created from a persisted XML data.
     *
     * <p>
     * See {@link User} class javadoc for more details about the life cycle
     * of {@link User} and when this method is invoked.
     *
     * @return null
     *      if the implementation choose not to add any property object for such user.
     */
    public abstract UserProperty newInstance(User user);

    /**
     * Whether or not the described property is enabled in the current context.
     * Defaults to true.  Over-ride in sub-classes as required.
     *
     * <p>
     * Returning false from this method essentially has the same effect of
     * making Hudson behaves as if this {@link UserPropertyDescriptor} is
     * not a part of {@link UserProperty#all()}.
     *
     * <p>
     * This mechanism is useful if the availability of the property is
     * contingent of some other settings.
     */
    public boolean isEnabled() {
        return true;
    }

    /**
     * Define the category for this user property descriptor.
     *
     * @return never null, always the same value for a given instance of {@link Descriptor}.
     *
     * @since 2.468
     */
    public @NonNull UserPropertyCategory getUserPropertyCategory() {
        // As this method is expected to be overloaded by subclasses
        // the logic here is just done to support plugins with older core version
        String categoryAsString = this.getUserPropertyCategoryAsString();
        if (categoryAsString != null) {
            Optional<UserPropertyCategory> firstIfFound = UserPropertyCategory.all().stream()
                    .filter(cat -> {
                        Symbol symbolAnnotation = cat.getClass().getAnnotation(Symbol.class);
                        if (symbolAnnotation != null) {
                            for (String symbolValue : symbolAnnotation.value()) {
                                if (symbolValue.equalsIgnoreCase(categoryAsString)) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    })
                    .findFirst();
            if (firstIfFound.isPresent()) {
                return firstIfFound.get();
            }
        }
        return UserPropertyCategory.get(UserPropertyCategory.Unclassified.class);
    }

    /**
     * Method proposed to prevent plugins to rely on too recent core version
     * while keeping the possibility to use the categories.
     *
     * @return String name corresponding to the symbol of {@link #getUserPropertyCategory()}
     *
     * @deprecated This should only be used when the core requirement is below the version this method was added
     *
     * @since 2.468
     */
    @Deprecated
    protected @CheckForNull String getUserPropertyCategoryAsString() {
        return null;
    }

    /**
     * Optional description for the configurable object
     * Displays as plain text
     *
     * @since 2.477
     */
    @Nullable
    public String getDescription() {
        return null;
    }
}
