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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.FormValidation;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import hudson.views.ViewJobFilter;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * {@link Descriptor} for {@link View}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.269
 */
public abstract class ViewDescriptor extends Descriptor<View> {
    /**
     * Returns the human-readable name of this type of view. Used
     * in the view creation screen. The string should look like
     * "Abc Def Ghi".
     */
    @NonNull
    @Override
    public String getDisplayName() {
        return super.getDisplayName();
    }

    /**
     * Some special views are not instantiable, and for those
     * this method returns false.
     */
    public boolean isInstantiable() {
        return true;
    }

    /**
     * Jelly fragment included in the "new view" page.
     */
    public final String getNewViewDetailPage() {
        return '/' + clazz.getName().replace('.', '/').replace('$', '/') + "/newViewDetail.jelly";
    }

    protected ViewDescriptor(Class<? extends View> clazz) {
        super(clazz);
    }

    protected ViewDescriptor() {
    }

    /**
     * Auto-completion for the "copy from" field in the new job page.
     */
    @Restricted(DoNotUse.class)
    public AutoCompletionCandidates doAutoCompleteCopyNewItemFrom(@QueryParameter final String value, @AncestorInPath ItemGroup<?> container) {
        AutoCompletionCandidates candidates = AutoCompletionCandidates.ofJobNames(TopLevelItem.class, value, container);
        if (container instanceof DirectlyModifiableTopLevelItemGroup) {
            DirectlyModifiableTopLevelItemGroup modifiableContainer = (DirectlyModifiableTopLevelItemGroup) container;
            Iterator<String> it = candidates.getValues().iterator();
            while (it.hasNext()) {
                TopLevelItem item = Jenkins.get().getItem(it.next(), container, TopLevelItem.class);
                if (item == null) {
                    continue; // ?
                }
                if (!modifiableContainer.canAdd(item)) {
                    it.remove();
                }
            }
        }
        return candidates;
    }

    /**
     * Possible {@link ListViewColumnDescriptor}s that can be used with this view.
     */
    public List<Descriptor<ListViewColumn>> getColumnsDescriptors() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request != null) {
            View view = request.findAncestorObject(clazz);
            return view == null ? DescriptorVisibilityFilter.applyType(clazz, ListViewColumn.all())
                    : DescriptorVisibilityFilter.apply(view, ListViewColumn.all());
        }
        return ListViewColumn.all();
    }

    /**
     * Possible {@link ViewJobFilter} types that can be used with this view.
     */
    public List<Descriptor<ViewJobFilter>> getJobFiltersDescriptors() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request != null) {
            View view = request.findAncestorObject(clazz);
            return view == null ? DescriptorVisibilityFilter.applyType(clazz, ViewJobFilter.all())
                    : DescriptorVisibilityFilter.apply(view, ViewJobFilter.all());
        }
        return ViewJobFilter.all();
    }

    /**
     * Validation of the display name field.
     *
     * @param view the view to check the new display name of.
     * @param value the proposed new display name.
     * @return the validation result.
     * @since 2.37
     */
    @SuppressWarnings("unused") // expose utility check method to subclasses
    protected FormValidation checkDisplayName(@NonNull View view, @CheckForNull String value) {
        if (value == null || value.isBlank()) {
            // no custom name, no need to check
            return FormValidation.ok();
        }
        for (View v : view.owner.getViews()) {
            if (v.getViewName().equals(view.getViewName())) {
                continue;
            }
            if (Objects.equals(v.getDisplayName(), value)) {
                return FormValidation.warning(Messages.View_DisplayNameNotUniqueWarning(value));
            }
        }
        return FormValidation.ok();
    }

    /**
     * Returns true if this {@link View} type is applicable to the given {@link ViewGroup} type.
     * <p>
     * Default implementation returns {@code true} always.
     *
     * @return true to indicate applicable, in which case the view will be instantiable within the type of owner.
     * @since 2.37
     */
    public boolean isApplicable(Class<? extends ViewGroup> ownerType) {
        return true;
    }

    /**
     * Returns true if this {@link View} type is applicable in the specific {@link ViewGroup}.
     * <p>
     * Default implementation returns {@link #isApplicable(Class)} for the {@link ViewGroup}’s {@link Object#getClass}.
     *
     * @return true to indicate applicable, in which case the view will be instantiable within the given owner.
     * @since 2.37
     */
    public boolean isApplicableIn(ViewGroup owner) {
        return isApplicable(owner.getClass());
    }

}
