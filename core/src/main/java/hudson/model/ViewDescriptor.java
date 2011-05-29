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

import jenkins.model.Jenkins;
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
    public abstract String getDisplayName();

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
    public AutoCompletionCandidates doAutoCompleteCopyNewItemFrom(@QueryParameter final String value) {
        final AutoCompletionCandidates r = new AutoCompletionCandidates();

        new ItemVisitor() {
            @Override
            public void onItemGroup(ItemGroup<?> group) {
                // only dig deep when the path matches what's typed.
                // for example, if 'foo/bar' is typed, we want to show 'foo/barcode'
                if (value.startsWith(group.getFullName()))
                    super.onItemGroup(group);
            }

            @Override
            public void onItem(Item i) {
                if (i.getFullName().startsWith(value)) {
                    r.add((i.getFullName()));
                    super.onItem(i);
                }
            }
        }.onItemGroup(Jenkins.getInstance());

        return r;
    }
}
