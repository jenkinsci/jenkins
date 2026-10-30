/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Adds {@link #getDescriptorByName(String)} to bind {@link Descriptor}s to URL.
 * Binding them at some specific object (instead of {@link jenkins.model.Jenkins}), allows
 * {@link Descriptor}s to perform context-specific form field validation.
 *
 * <p>
 * {@link Descriptor#getCheckUrl(String)} finds an ancestor with this interface
 * and generates URLs against it.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.294
 * @see Descriptor#getCheckUrl(String)
 */
public interface DescriptorByNameOwner extends ModelObject {
    /**
     * Exposes all {@link Descriptor}s by its name to URL.
     *
     * <p>
     * Implementation should always delegate to {@link jenkins.model.Jenkins#getDescriptorByName(String)}.
     *
     * @param id
     *      Either {@link Descriptor#getId()} (recommended) or the short name.
     */
    default Descriptor getDescriptorByName(String id) {
        return Jenkins.get().getDescriptorByName(id);
    }

    /**
     * The permission needed on this object to render, at {@code descriptorByName/ID/page/...}, the configuration page
     * of a descriptor for a new instance, as the configuration pages of this object offer descriptors to add or select.
     * It should be the permission to configure this object; by default, {@link Jenkins#ADMINISTER}.
     *
     * @since TODO
     * @see hudson.widgets.DescriptorPage
     */
    @Restricted(Beta.class)
    default Permission getDescriptorPagePermission() {
        return Jenkins.ADMINISTER;
    }
}
