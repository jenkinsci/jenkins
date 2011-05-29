/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.tools;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

/**
 * Extensible property of {@link ToolInstallation}.
 *
 * <p>
 * Plugins can contribute this extension point to add additional data or UI actions to {@link ToolInstallation}.
 * {@link ToolProperty}s show up in the configuration screen of a tool, and they are persisted with the {@link ToolInstallation} object.
 *
 *
 * <h2>Views</h2>
 * <dl>
 * <dt>config.jelly</dt>
 * <dd>Added to the configuration page of the tool.
 * </dl>
 *
 * @param <T>
 *      {@link ToolProperty} can choose to only work with a certain subtype of {@link ToolInstallation}, and this 'T'
 *      represents that type. Also see {@link ToolPropertyDescriptor#isApplicable(Class)}.
 *
 * @since 1.303
 */
public abstract class ToolProperty<T extends ToolInstallation> implements Describable<ToolProperty<?>>, ExtensionPoint {
    protected transient T tool;

    protected void setTool(T tool) {
        this.tool = tool;
    }

    public ToolPropertyDescriptor getDescriptor() {
        return (ToolPropertyDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * What is your 'T'?
     */
    public abstract Class<T> type();

    /**
     * Lists up all the registered {@link ToolPropertyDescriptor}s in the system.
     *
     * @see ToolDescriptor#getPropertyDescriptors() 
     */
    public static DescriptorExtensionList<ToolProperty<?>,ToolPropertyDescriptor> all() {
        return (DescriptorExtensionList) Jenkins.getInstance().getDescriptorList(ToolProperty.class);
    }
}
