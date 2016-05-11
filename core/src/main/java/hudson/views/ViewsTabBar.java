/*
 * The MIT License
 * 
 * Copyright (c) 2010, Winston.Prakash@oracle.com, CloudBees, Inc.
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
package hudson.views;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import hudson.model.ListView;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Extension point for adding a ViewsTabBar header to Projects {@link ListView}.
 *
 * <p>
 * This object must have the <tt>viewTabs.jelly</tt>. This view
 * is called once when the project views main panel is built.
 * The "views" attribute is set to the "Collection of views".
 *
 * <p>
 * There also must be a default constructor, which is invoked to create a Views TabBar in
 * the default configuration.
 *
 * @author Winston Prakash
 * @since 1.381
 * @see ViewsTabBarDescriptor
 */
public abstract class ViewsTabBar extends AbstractDescribableImpl<ViewsTabBar> implements ExtensionPoint {
    /**
     * Returns all the registered {@link ViewsTabBar} descriptors.
     */
    public static DescriptorExtensionList<ViewsTabBar, Descriptor<ViewsTabBar>> all() {
        return Jenkins.getInstance().<ViewsTabBar, Descriptor<ViewsTabBar>>getDescriptorList(ViewsTabBar.class);
    }

    @Override
    public ViewsTabBarDescriptor getDescriptor() {
        return (ViewsTabBarDescriptor)super.getDescriptor();
    }

    /**
     * Configures {@link ViewsTabBar} in the system configuration.
     *
     * @author Kohsuke Kawaguchi
     */
    @Extension(ordinal=310) @Symbol("viewsTabBar")
    public static class GlobalConfigurationImpl extends GlobalConfiguration {
        public ViewsTabBar getViewsTabBar() {
            return Jenkins.getInstance().getViewsTabBar();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            // for compatibility reasons, the actual value is stored in Jenkins
            Jenkins j = Jenkins.getInstance();

            if (json.has("viewsTabBar")) {
                j.setViewsTabBar(req.bindJSON(ViewsTabBar.class,json.getJSONObject("viewsTabBar")));
            } else {
                j.setViewsTabBar(new DefaultViewsTabBar());
            }

            return true;
        }
    }
}
