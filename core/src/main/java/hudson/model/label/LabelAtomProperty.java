/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.label;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Hudson;
import hudson.slaves.NodeDescriptor;
import hudson.slaves.NodePropertyDescriptor;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Extensible property of {@link LabelAtom}.
 *
 * <p>
 * Plugins can contribute this extension point to add additional data or UI actions to {@link LabelAtom}.
 * {@link LabelAtomProperty}s show up in the configuration screen of a label, and they are persisted
 * with the {@link LabelAtom} object.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.373
 */
@ExportedBean
public class LabelAtomProperty extends AbstractDescribableImpl<LabelAtomProperty> implements ExtensionPoint {
    /**
     * Lists up all the registered {@link LabelAtomPropertyDescriptor}s in the system.
     */
    public static DescriptorExtensionList<LabelAtomProperty,LabelAtomPropertyDescriptor> all() {
        return Hudson.getInstance().getDescriptorList(LabelAtomProperty.class);
    }
}
