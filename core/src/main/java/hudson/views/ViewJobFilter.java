/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt
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
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import hudson.model.TopLevelItem;
import hudson.model.View;

import java.util.List;

/**
 * Each ViewJobFilter contributes to or removes from the list of Jobs for a view.
 *
 * @author Jacob Robertson
 */
public abstract class ViewJobFilter implements ExtensionPoint, Describable<ViewJobFilter> {

    /**
     * Returns all the registered {@link ViewJobFilter} descriptors.
     */
    public static DescriptorExtensionList<ViewJobFilter, Descriptor<ViewJobFilter>> all() {
        return Jenkins.getInstance().<ViewJobFilter, Descriptor<ViewJobFilter>>getDescriptorList(ViewJobFilter.class);
    }

    @SuppressWarnings("unchecked")
	public Descriptor<ViewJobFilter> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }
    
    /**
     * Choose which jobs to show for a view.
     * @param added which jobs have been added so far.  This JobFilter can remove or add to this list.
     * @param all All jobs that are possible.
     * @param filteringView The view that we are filtering jobs for.
     * @return a new list based off of the jobs added so far, and all jobs available.
     */
    abstract public List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView);
}
