/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc., Stephen Connolly
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

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Extensible property of {@link View}.
 *
 * <p>
 * {@link hudson.Plugin}s can extend this to define custom properties for {@link View}s.
 * {@link ViewProperty}s show up in the view configuration screen, and they are
 * persisted with the view object.
 *
 * <p>
 * Configuration screen should be defined in {@code config.jelly}.
 * Within this page, the {@link ViewProperty} instance is available as
 * the {@code instance} EL variable (while the {@code it} EL variable
 * refers to the {@link View}.
 *
 * @author Stephen Connolly
 * @since 1.406
 */
public class ViewProperty implements ReconfigurableDescribable<ViewProperty>, ExtensionPoint {
    /**
     * The view object that owns this property.
     * This value will be set by the core code.
     * Derived classes can expect this value to be always set.
     */
    protected transient View view;

    /*package*/ final void setView(View view) {
        this.view = view;
    }

    @Override
    public ViewPropertyDescriptor getDescriptor() {
        return (ViewPropertyDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<ViewProperty, ViewPropertyDescriptor> all() {
        return Jenkins.get().getDescriptorList(ViewProperty.class);
    }

    @Override
    public ViewProperty reconfigure(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (Util.isOverridden(ViewProperty.class, getClass(), "reconfigure", StaplerRequest.class, JSONObject.class)) {
            return reconfigure(StaplerRequest.fromStaplerRequest2(req), form);
        } else {
            return reconfigureImpl(req, form);
        }
    }

    @Deprecated
    @Override
    public ViewProperty reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return reconfigureImpl(StaplerRequest.toStaplerRequest2(req), form);
    }

    private ViewProperty reconfigureImpl(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        return form == null ? null : getDescriptor().newInstance(req, form);
    }
}
