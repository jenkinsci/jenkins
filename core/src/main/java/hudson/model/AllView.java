/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;

import hudson.model.Descriptor.FormException;
import hudson.Extension;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * {@link View} that contains everything.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.269
 */
public class AllView extends View {
    @DataBoundConstructor
    public AllView(String name) {
        super(name);
    }

    public AllView(String name, ViewGroup owner) {
        this(name);
        this.owner = owner;
    }
    
    @Override
    public boolean isEditable() {
        return false;
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return true;
    }

    @RequirePOST
    @Override
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        ItemGroup<? extends TopLevelItem> ig = getOwnerItemGroup();
        if (ig instanceof ModifiableItemGroup)
            return ((ModifiableItemGroup<? extends TopLevelItem>)ig).doCreateItem(req, rsp);
        return null;
    }

    @Override
    public Collection<TopLevelItem> getItems() {
        return (Collection)getOwnerItemGroup().getItems();
    }

    @Override
    public String getPostConstructLandingPage() {
        return ""; // there's no configuration page
    }

    @Override
    protected void submit(StaplerRequest req) throws IOException, ServletException, FormException {
        // noop
    }

    @Extension
    public static final class DescriptorImpl extends ViewDescriptor {
        @Override
        public boolean isInstantiable() {
            for (View v : Stapler.getCurrentRequest().findAncestorObject(ViewGroup.class).getViews())
                if(v instanceof AllView)
                    return false;
            return true;
        }

        public String getDisplayName() {
            return Messages.Hudson_ViewName();
        }
    }
}
