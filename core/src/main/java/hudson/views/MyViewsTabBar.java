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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.MyViewsProperty;
import hudson.model.View;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Extension point for adding a MyViewsTabBar header to Projects {@link MyViewsProperty}.
 *
 * <p>
 * This object must have the {@code myViewTabs.jelly}. This view
 * is called once when the My Views main panel is built.
 * The "views" attribute is set to the "Collection of views".
 *
 * <p>
 * There also must be a default constructor, which is invoked to create a My Views TabBar in
 * the default configuration.
 *
 * @author Winston Prakash
 * @since 1.378
 * @see MyViewsTabBarDescriptor
 */
public abstract class MyViewsTabBar implements Describable<MyViewsTabBar>, ExtensionPoint {
    /**
     * Returns all the registered {@link ListViewColumn} descriptors.
     */
    public static DescriptorExtensionList<MyViewsTabBar, Descriptor<MyViewsTabBar>> all() {
        return Jenkins.get().getDescriptorList(MyViewsTabBar.class);
    }

    @Override
    public MyViewsTabBarDescriptor getDescriptor() {
        return (MyViewsTabBarDescriptor) Describable.super.getDescriptor();
    }

    /**
     * Sorts the views by {@link View#getDisplayName()}.
     *
     * @param views the views.
     * @return the sorted views
     * @since 2.37
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // invoked from stapler view
    public List<View> sort(@NonNull List<? extends View> views) {
        List<View> result = new ArrayList<>(views);
        result.sort(Comparator.comparing(View::getDisplayName));
        return result;
    }

    /**
     * Configures {@link ViewsTabBar} in the system configuration.
     *
     * @author Kohsuke Kawaguchi
     */
    @Extension(ordinal = 305) @Symbol("myView")
    public static class GlobalConfigurationImpl extends GlobalConfiguration {
        public MyViewsTabBar getMyViewsTabBar() {
            return Jenkins.get().getMyViewsTabBar();
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
            // for compatibility reasons, the actual value is stored in Jenkins
            Jenkins j = Jenkins.get();

            if (json.has("myViewsTabBar")) {
                j.setMyViewsTabBar(req.bindJSON(MyViewsTabBar.class, json.getJSONObject("myViewsTabBar")));
            } else {
                j.setMyViewsTabBar(new DefaultMyViewsTabBar());
            }

            return true;
        }
    }
}
