/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Descriptor.FormException;
import hudson.model.userproperty.UserPropertyCategory;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Extensible property of {@link User}.
 *
 * <p>
 * Plugins can extend this to define custom properties
 * for {@link User}s. {@link UserProperty}s show up in the user
 * configuration screen, and they are persisted with the user object.
 *
 * <p>
 * Configuration screen should be defined in {@code config.jelly}.
 * Within this page, the {@link UserProperty} instance is available
 * as {@code instance} variable (while {@code it} refers to {@link User}.
 * See {@link hudson.search.UserSearchProperty}'s {@code config.jelly} for an example.
 * <p>A property may also define a {@code summary.jelly} view to show in the main user screen.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class UserProperty implements ReconfigurableDescribable<UserProperty>, ExtensionPoint {
    /**
     * The user object that owns this property.
     * This value will be set by the Hudson code.
     * Derived classes can expect this value to be always set.
     */
    protected transient User user;

    /**
     * This method is used to inform the property about its owner.
     * It could be called multiple times, even without change, thus it should be idempotent.
     */
    protected void setUser(User u) {
        this.user = u;
    }

    // descriptor must be of the UserPropertyDescriptor type
    @Override
    public UserPropertyDescriptor getDescriptor() {
        return (UserPropertyDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Returns all the registered {@link UserPropertyDescriptor}s.
     */
    public static DescriptorExtensionList<UserProperty, UserPropertyDescriptor> all() {
        return Jenkins.get().getDescriptorList(UserProperty.class);
    }

    /**
     * Returns all the registered {@link UserPropertyCategory} descriptors for a given category.
     *
     * @since 2.468
     */
    public static List<UserPropertyDescriptor> allByCategoryClass(@NonNull Class<? extends UserPropertyCategory> categoryClass) {
        DescriptorExtensionList<UserProperty, UserPropertyDescriptor> all = all();

        List<UserPropertyDescriptor> onlyForTheCategory = new ArrayList<>(all.size());
        for (UserPropertyDescriptor descriptor : all) {
            if (descriptor.getUserPropertyCategory().getClass().equals(categoryClass)) {
                onlyForTheCategory.add(descriptor);
            }
        }

        return onlyForTheCategory;
    }

    @Override
    public UserProperty reconfigure(StaplerRequest2 req, JSONObject form) throws FormException {
        return form == null ? null : getDescriptor().newInstance(req, form);
    }
}
