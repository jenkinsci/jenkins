/*
 * The MIT License
 *
 * Copyright (c) 2009, Sun Microsystems, Inc.
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
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.List;
import java.util.ArrayList;

/**
 * Descriptor for a {@link ToolInstaller}.
 * @since 1.305
 */
public abstract class ToolInstallerDescriptor<T extends ToolInstaller> extends Descriptor<ToolInstaller> {

    /**
     * Controls what kind of {@link ToolInstallation} this installer can be applied to.
     *
     * <p>
     * By default, this method just returns true to everything, claiming it's applicable to any tool installations.
     */
    public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
        return true;
    }

    public static DescriptorExtensionList<ToolInstaller,ToolInstallerDescriptor<?>> all() {
        return Jenkins.getInstance().<ToolInstaller,ToolInstallerDescriptor<?>>getDescriptorList(ToolInstaller.class);
    }

    /**
     * Filters {@link #all()} by eliminating things that are not applicable to the given type.
     */
    public static List<ToolInstallerDescriptor<?>> for_(Class<? extends ToolInstallation> type) {
        List<ToolInstallerDescriptor<?>> r = new ArrayList<ToolInstallerDescriptor<?>>();
        for (ToolInstallerDescriptor<?> d : all())
            if(d.isApplicable(type))
                r.add(d);
        return r;
    }

}
