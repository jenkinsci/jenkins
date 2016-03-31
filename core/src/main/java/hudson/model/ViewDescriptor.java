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

import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import hudson.views.ViewJobFilter;
import java.util.Iterator;
import java.util.List;
import jenkins.model.DirectlyModifiableTopLevelItemGroup;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

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
        return '/'+clazz.getName().replace('.','/').replace('$','/')+"/newViewDetail.jelly";
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
                TopLevelItem item = Jenkins.getInstance().getItem(it.next(), container, TopLevelItem.class);
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
        return ListViewColumn.all();
    }

    /**
     * Possible {@link ViewJobFilter} types that can be used with this view.
     */
    public List<Descriptor<ViewJobFilter>> getJobFiltersDescriptors() {
        return ViewJobFilter.all();
    }
}
