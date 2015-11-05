/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import hudson.model.Descriptor.FormException;
import hudson.slaves.NodeProperty;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Marks modern {@link Describable}s that allow the current instances to pass information down to the next
 * instance when the configuration is submitted.
 *
 * <p>
 * As the generics signature implies, it isn't up to individual {@link Describable} implementation to
 * implement this. Rather, it is up to the designer of an extension point to mark the extension point
 * as {@link ReconfigurableDescribable}, as it requires coordination at the owner of the extension point.
 *
 * <h1>Use Cases</h1>
 * <h2>Invisible Property</h2>
 * <p>
 * This mechanism can be used to create an entirely invisible {@link Describable}, which is handy
 * for {@link NodeProperty}, {@link JobProperty}, etc. To do so, define a descriptor with null
 * {@linkplain Descriptor#getDisplayName() display name} and empty config.jelly to prevent it from
 * showing up in the config UI, then implement {@link #reconfigure(StaplerRequest, JSONObject)}
 * and simply return {@code this}.
 *
 * <h2>Passing some values without going through clients</h2>
 * <p>
 * Sometimes your {@link Describable} object may have some expensive objects that you might want to
 * hand over to the next instance. This hook lets you do that.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.406
 */
public interface ReconfigurableDescribable<T extends ReconfigurableDescribable<T>> extends Describable<T> {

    /**
     * When a parent/owner object of a Describable gets a config form submission and instances are
     * recreated, this method is invoked on the existing instance (meaning the 'this' reference
     * points to the existing instance) to create a new instance to be added to the parent/owner object.
     *
     * <p>
     * The default implementation of this should be the following:
     * <pre>
     * return form==null ? null : getDescriptor().newInstance(req, form);
     * </pre>
     *
     * @param req
     *      The current HTTP request being processed.
     * @param form
     *      JSON fragment that corresponds to this describable object.
     *      If the newly submitted form doesn't include a fragment for this describable
     *      (meaning the user has de-selected your descriptor), then this argument is null.
     *
     * @return
     *      The new instance. To not to create an instance of a describable, return null.
     */
    @CheckForNull T reconfigure(@Nonnull StaplerRequest req, @CheckForNull JSONObject form) throws FormException;
}
