/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

package org.jvnet.hudson.test;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.AllView;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.model.ViewGroupMixIn;
import hudson.util.Function1;
import hudson.views.DefaultViewsTabBar;
import hudson.views.ViewsTabBar;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;

/**
 * Minimal implementation of a modifiable item group akin to the CloudBees Folders plugin.
 * No UI, just enough implementation to test functionality of code which should deal with item full names, etc.
 * @since 1.494
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // the usual API mistakes
public class MockFolder extends AbstractItem implements ModifiableTopLevelItemGroup, TopLevelItem, ViewGroup, StaplerFallback {

    private transient Map<String,TopLevelItem> items = new TreeMap<String,TopLevelItem>();
    private final List<View> views = new ArrayList<View>(Collections.singleton(new AllView("All", this)));
    private String primaryView;
    private ViewsTabBar viewsTabBar;

    private MockFolder(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        items = ItemGroupMixIn.loadChildren(this, jobs(), new Function1<String,TopLevelItem>() {
            public String call(TopLevelItem item) {
               return item.getName();
            }
        });
    }

    @Override public Collection<TopLevelItem> getItems() {
        return items.values(); // could be filtered by Item.READ
    }

    @Override public TopLevelItem getItem(String name) {
        return items.get(name);
    }

    @Override public Collection<? extends Job> getAllJobs() {
        Set<Job> jobs = new HashSet<Job>();
        for (TopLevelItem i : getItems()) {
            jobs.addAll(i.getAllJobs());
        }
        return jobs;
    }
    
    private File jobs() {
        return new File(getRootDir(), "jobs");
    }

    private ItemGroupMixIn mixin() {
        return new ItemGroupMixIn(this, this) {
            @Override protected void add(TopLevelItem item) {
                items.put(item.getName(), item);
            }
            @Override protected File getRootDirFor(String name) {
                return new File(jobs(), name);
            }
        };
    }

    private ViewGroupMixIn vgmixin() {
        return new ViewGroupMixIn(this) {
            @Override protected List<View> views() {
                return views;
            }
            @Override protected String primaryView() {
                return primaryView != null ? primaryView : views.get(0).getViewName();
            }
            @Override protected void primaryView(String newName) {
                primaryView = newName;
            }
        };
    }

    @Override public <T extends TopLevelItem> T copy(T src, String name) throws IOException {
        return mixin().copy(src, name);
    }

    @Override public void onCopiedFrom(Item src) {
        super.onCopiedFrom(src);
        for (TopLevelItem item : ((MockFolder) src).getItems()) {
            try {
                copy(item, item.getName());
            } catch (IOException x) {
                assert false : x;
            }
        }
    }

    @Override public TopLevelItem createProjectFromXML(String name, InputStream xml) throws IOException {
        return mixin().createProjectFromXML(name, xml);
    }

    @Override public TopLevelItem createProject(TopLevelItemDescriptor type, String name, boolean notify) throws IOException {
        return mixin().createProject(type, name, notify);
    }

    /** Convenience method to create a {@link FreeStyleProject} or similar. */
    public <T extends TopLevelItem> T createProject(Class<T> type, String name) throws IOException {
        return type.cast(createProject((TopLevelItemDescriptor) Jenkins.getInstance().getDescriptor(type), name, true));
    }

    @Override public TopLevelItem doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        return mixin().createTopLevelItem(req, rsp);
    }

    @Override public String getUrlChildPrefix() {
        return "job";
    }

    @Override public File getRootDirFor(TopLevelItem child) {
        return new File(jobs(), child.getName());
    }

    @Override public void onRenamed(TopLevelItem item, String oldName, String newName) throws IOException {
        items.remove(oldName);
        items.put(newName, item);
    }

    @Override public void onDeleted(TopLevelItem item) throws IOException {
        // could call ItemListener.onDeleted
        items.remove(item.getName());
    }

    @Override public TopLevelItemDescriptor getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    public void addView(View view) throws IOException {
        vgmixin().addView(view);
    }

    @Override public boolean canDelete(View view) {
        return vgmixin().canDelete(view);
    }

    @Override public void deleteView(View view) throws IOException {
        vgmixin().deleteView(view);
    }

    @Override public Collection<View> getViews() {
        return vgmixin().getViews();
    }

    @Override public View getView(String name) {
        return vgmixin().getView(name);
    }

    @Override public View getPrimaryView() {
        return vgmixin().getPrimaryView();
    }

    @Override public void onViewRenamed(View view, String oldName, String newName) {
        vgmixin().onViewRenamed(view, oldName, newName);
    }

    @Override public ViewsTabBar getViewsTabBar() {
        if (viewsTabBar == null) {
            viewsTabBar = new DefaultViewsTabBar();
        }
        return viewsTabBar;
    }

    @Override public ItemGroup<? extends TopLevelItem> getItemGroup() {
        return this;
    }

    @Override public List<Action> getViewActions() {
        // TODO what should the default be? View.getOwnerViewActions uses Jenkins.actions; Jenkins.viewActions would make more sense as a default;
        // or should it be empty by default since non-top-level folders probably do not need the same actions as root?
        return Collections.emptyList();
    }

    @Override public Object getStaplerFallback() {
        return getPrimaryView();
    }

    /**
     * Same as {@link #getItem} but named this way as a {@link WebMethod}.
     * @see Hudson#getJob
     */
    public TopLevelItem getJob(String name) {
        return getItem(name);
    }

    @Extension public static class DescriptorImpl extends TopLevelItemDescriptor {

        @Override public String getDisplayName() {
            return "MockFolder";
        }

        @Override public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new MockFolder(parent, name);
        }

    }

}
