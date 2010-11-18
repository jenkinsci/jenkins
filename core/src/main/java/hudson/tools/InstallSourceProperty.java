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

import hudson.Extension;
import hudson.util.DescribableList;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;
import java.io.IOException;

/**
 * {@link ToolProperty} that shows auto installation options.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.305
 */
public class InstallSourceProperty extends ToolProperty<ToolInstallation> {
    // TODO: get the proper Saveable
    public final DescribableList<ToolInstaller, Descriptor<ToolInstaller>> installers =
            new DescribableList<ToolInstaller, Descriptor<ToolInstaller>>(Saveable.NOOP);

    @DataBoundConstructor
    public InstallSourceProperty(List<? extends ToolInstaller> installers) throws IOException {
        if (installers != null) {
            this.installers.replaceBy(installers);
        }
    }

    @Override
    public void setTool(ToolInstallation t) {
        super.setTool(t);
        for (ToolInstaller installer : installers)
            installer.setTool(t);
    }

    public Class<ToolInstallation> type() {
        return ToolInstallation.class;
    }

    @Extension
    public static class DescriptorImpl extends ToolPropertyDescriptor {
        public String getDisplayName() {
            return Messages.InstallSourceProperty_DescriptorImpl_displayName();
        }
    }
}
