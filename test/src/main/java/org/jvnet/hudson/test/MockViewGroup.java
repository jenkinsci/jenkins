/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.Action;
import hudson.model.Descriptor.FormException;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.views.ViewsTabBar;

public class MockViewGroup extends View implements ViewGroup {

    final List<View> views = new ArrayList<View>();
    final List<TopLevelItem> items = new ArrayList<TopLevelItem>();

    public MockViewGroup(String name) {
        super(name);
    }

    public MockViewGroup(String name, ViewGroup owner) {
        super(name, owner);
    }

    @Override
    public boolean canDelete(View view) {
        return true;
    }

    @Override
    public void deleteView(View view) throws IOException {
        views.remove(view);
    }

    @Override
    public Collection<View> getViews() {
        return views;
    }

    @Override
    public View getView(String name) {
        for (View view: views) {
            if (view.getViewName().equals(name)) return view;
        }
        return null;
    }

    @Override
    public View getPrimaryView() {
        return null;
    }

    @Override
    public void onViewRenamed(View view, String oldName, String newName) {
    }

    @Override
    public ViewsTabBar getViewsTabBar() {
        return getOwner().getViewsTabBar();
    }

    @Override
    public ItemGroup<? extends TopLevelItem> getItemGroup() {
        return getOwnerItemGroup();
    }

    @Override
    public List<Action> getViewActions() {
        return getOwner().getViewActions();
    }

    @Override
    public Collection<TopLevelItem> getItems() {
        return items;
    }

    @Override
    public boolean contains(TopLevelItem item) {
        return items.contains(item);
    }

    @Override
    protected void submit(StaplerRequest req) throws IOException, ServletException, FormException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        throw new UnsupportedOperationException();
    }
}
