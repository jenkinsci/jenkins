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


import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Marker interface for {@link View} its items can be modified.
 *
 * @author ogondza
 * @since 1.566
 */
public interface DirectlyModifiableView {

    /**
     * Remove item from this view.
     *
     * @return false if item not present in view, true if removed.
     * @throws IOException Removal failed.
     * @throws IllegalArgumentException View rejected to remove an item.
     */
    boolean remove(@NonNull TopLevelItem item) throws IOException, IllegalArgumentException;

    /**
     * Add item to this view.
     *
     * @throws IOException Adding failed.
     * @throws IllegalArgumentException View rejected to add an item.
     */
    void add(@NonNull TopLevelItem item) throws IOException, IllegalArgumentException;

    /**
     * Handle addJobToView web method.
     *
     * This method should {@link RequirePOST}.
     *
     * @param name Item name. This can be either full name relative to owner item group or full item name prefixed with '/'.
     */
    HttpResponse doAddJobToView(@QueryParameter String name) throws IOException, ServletException;

    /**
     * Handle removeJobFromView web method.
     *
     * This method should {@link RequirePOST}.
     *
     * @param name Item name. This can be either full name relative to owner item group or full item name prefixed with '/'.
     */
    HttpResponse doRemoveJobFromView(@QueryParameter String name) throws IOException, ServletException;
}
