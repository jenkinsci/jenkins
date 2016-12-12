/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe, Tom Huybrechts
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.model.Descriptor.FormException;
import hudson.Extension;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * {@link View} that only contains projects for which the current user has access to.
 *
 * @since 1.220
 * @author Tom Huybrechts
 */
public class MyView extends View {
    @DataBoundConstructor
    public MyView(String name) {
        super(name);
    }

    public MyView(String name, ViewGroup owner) {
        this(name);
        this.owner = owner;
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return item.hasPermission(Item.CONFIGURE);
    }

    @RequirePOST
    @Override
    public TopLevelItem doCreateItem(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        ItemGroup<? extends TopLevelItem> ig = getOwnerItemGroup();
        if (ig instanceof ModifiableItemGroup) {
            return ((ModifiableItemGroup<? extends TopLevelItem>)ig).doCreateItem(req, rsp);
        }
        return null;
    }

    @Override
    public Collection<TopLevelItem> getItems() {
        List<TopLevelItem> items = new ArrayList<TopLevelItem>();
        for (TopLevelItem item : getOwnerItemGroup().getItems()) {
            if (item.hasPermission(Item.CONFIGURE)) {
                items.add(item);
            }
        }
        return Collections.unmodifiableList(items);
    }

    @Override
    public String getPostConstructLandingPage() {
        return ""; // there's no configuration page
    }

    @Override
    protected void submit(StaplerRequest req) throws IOException, ServletException, FormException {
        // noop
    }

    @Extension @Symbol("myView")
    public static final class DescriptorImpl extends ViewDescriptor {
        /**
         * If the security is not enabled, there's no point in having
         * this type of views.
         */
        @Override
        public boolean isInstantiable() {
            return Jenkins.getInstance().isUseSecurity();
        }

        public String getDisplayName() {
            return Messages.MyView_DisplayName();
        }
    }
}
