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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.util.Iterators;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * {@link Descriptor} for {@link TopLevelItem}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class TopLevelItemDescriptor extends Descriptor<TopLevelItem> {
    protected TopLevelItemDescriptor(Class<? extends TopLevelItem> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link TopLevelItem} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     *
     * @since 1.278
     */
    protected TopLevelItemDescriptor() {
    }

    /**
     * {@link TopLevelItemDescriptor}s often uses other descriptors to decorate itself.
     * This method allows the subtype of {@link TopLevelItemDescriptor}s to filter them out.
     *
     * <p>
     * This is useful for a workflow/company specific job type that wants to eliminate
     * options that the user would see.
     *
     * @since 1.294
     */
    public boolean isApplicable(Descriptor descriptor) {
        return true;
    }

    /**
     * Tests if the given instance belongs to this descriptor, in the sense
     * that this descriptor can produce items like the given one.
     *
     * <p>
     * {@link TopLevelItemDescriptor}s that act like a wizard and produces different
     * object types than {@link #clazz} can override this method to augment
     * instance-descriptor relationship.
     */
    public boolean testInstance(TopLevelItem i) {
        return clazz.isInstance(i);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Used as the caption when the user chooses what job type to create.
     * The descriptor implementation also needs to have <tt>newJobDetail.jelly</tt>
     * script, which will be used to render the text below the caption
     * that explains the job type.
     */
    public abstract String getDisplayName();

    /**
     * @deprecated since 2007-01-19.
     *      This is not a valid operation for {@link Job}s.
     */
    @Deprecated
    public TopLevelItem newInstance(StaplerRequest req) throws FormException {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new {@link TopLevelItem}.
     *
     * @deprecated as of 1.390
     *      Use {@link #newInstance(ItemGroup, String)}
     */
    public TopLevelItem newInstance(String name) {
        return newInstance(Jenkins.getInstance(), name);
    }

    /**
     * Creates a new {@link TopLevelItem} for the specified parent.
     *
     * @since 1.390
     */
    public abstract TopLevelItem newInstance(ItemGroup parent, String name);

    /**
     * Returns all the registered {@link TopLevelItem} descriptors.
     */
    public static ExtensionList<TopLevelItemDescriptor> all() {
        return Items.all();
    }

    /**
     * Returns the ACL for this top level item descriptor in the context of the specified {@link ItemGroup}.
     *
     * @param context the context
     * @return the ACL
     * @since 1.574
     */
    @Nonnull
    public ACL getACL(@Nonnull ItemGroup context) {
        return Jenkins.getInstance().getAuthorizationStrategy().getACL(context, this);
    }

    /**
     * Returns the ACL for this top level item descriptor in the context of the {@link ItemGroup} inferred
     * from the current {@link StaplerRequest} or {@link Jenkins} if invoked outside of a {@link StaplerRequest}.
     *
     * @return the ACL
     * @since 1.574
     */
    @Nonnull
    public ACL getACL() {
        final StaplerRequest request = Stapler.getCurrentRequest();
        if (request != null) {
            List<Ancestor> ancs = request.getAncestors();
            for (Ancestor anc : Iterators.reverse(ancs)) {
                Object o = anc.getObject();
                if (o instanceof ItemGroup) {
                    return getACL((ItemGroup) o);
                }
            }
        }
        return getACL(Jenkins.getInstance());
    }

    /**
     * Returns the {@code true} if and only if the current user has the specified permission the context of the
     * {@link ItemGroup} inferred from the current {@link StaplerRequest} or {@link Jenkins} if invoked outside
     * of a {@link StaplerRequest} in which case the current user will most likely have been inferred as
     * {@link ACL#SYSTEM} anyway.
     *
     * @param p the permission
     * @return the {@code true} if the current user has the permission.
     * @since 1.574
     */
    public final boolean hasPermission(@Nonnull Permission p) {
        return hasPermission(Jenkins.getAuthentication(),p);
    }

    /**
     * Returns the {@code true} if and only if the specified authentication has the specified permission the context
     * of the {@link ItemGroup} inferred from the current {@link StaplerRequest} or {@link Jenkins} if invoked outside
     * of a {@link StaplerRequest}.
     *
     * @param a          the authentication
     * @param permission the permission
     * @return the {@code true} if the authentication has the permission.
     * @since 1.574
     */
    public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission) {
        return getACL().hasPermission(a, permission);
    }

    /**
     * An extension that controls descriptor visibility based on the ACLs defined for each
     * {@link TopLevelItemDescriptor}
     *
     * @since 1.574
     */
    @Extension
    public static class CreatePermissionVisibilityFilter extends DescriptorVisibilityFilter {

        /** {@inheritDoc} */
        @Override
        public boolean filter(Object context, Descriptor descriptor) {
            if (descriptor instanceof TopLevelItemDescriptor && context instanceof ItemGroup) {
                return ((TopLevelItemDescriptor) descriptor).getACL((ItemGroup) context).hasPermission(Item.CREATE);
            }
            return true;
        }
    }

}
