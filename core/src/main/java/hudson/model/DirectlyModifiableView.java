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
package hudson.model;

import hudson.util.HttpResponses;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * View its items can be modified.
 *
 * @author ogondza
 * @since TODO
 */
public abstract class DirectlyModifiableView extends View {

    public DirectlyModifiableView(String name) {
        super(name);
    }

    public DirectlyModifiableView(String name, ViewGroup owner) {
        super(name, owner);
    }

    /**
     * Remove item from this view.
     *
     * @return false if item not present in view, true if removed.
     * @throws IOException Removal failed.
     * @throws IllegalArgumentException View rejected to remove an item.
     */
    public abstract boolean remove(@Nonnull TopLevelItem item) throws IOException, IllegalArgumentException;

    /**
     * Add item to this view.
     *
     * @throws IOException Adding failed.
     * @throws IllegalArgumentException View rejected to add an item.
     */
    public abstract void add(@Nonnull TopLevelItem item) throws IOException, IllegalArgumentException;

    /**
     * Handle addJobToView web method.
     *
     * @param name Item name.
     */
    @RequirePOST
    public HttpResponse doAddJobToView(@QueryParameter String name) throws IOException, ServletException {
        checkPermission(View.CONFIGURE);
        if(name==null)
            throw new Failure("Query parameter 'name' is required");

        TopLevelItem item = getOwnerItemGroup().getItem(name);
        if (item == null)
            throw new Failure("Query parameter 'name' does not correspond to a known item");

        if (contains(item)) return HttpResponses.ok();

        add(item);
        owner.save();

        return HttpResponses.ok();
    }

    /**
     * Handle removeJobFromView web method.
     *
     * @param name Item name.
     */
    @RequirePOST
    public HttpResponse doRemoveJobFromView(@QueryParameter String name) throws IOException, ServletException {
        checkPermission(View.CONFIGURE);
        if(name==null)
            throw new Failure("Query parameter 'name' is required");

        TopLevelItem item = getOwnerItemGroup().getItem(name);
        if (remove(item))
            owner.save();

        return HttpResponses.ok();
    }
}
