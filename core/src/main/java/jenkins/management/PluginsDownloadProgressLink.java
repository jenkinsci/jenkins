/*
 * The MIT License
 *
 * Copyright (c) 2025, Jan Faracik
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

package jenkins.management;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jenkins.model.experimentalflags.NewManageJenkinsUserExperimentalFlag;

@Extension(ordinal = Integer.MAX_VALUE - 3)
public class PluginsDownloadProgressLink extends ManagementLink {

    @Override
    public String getIconFileName() {
        var flagEnabled = new NewManageJenkinsUserExperimentalFlag().getFlagValue();

        if (!flagEnabled) {
            return null;
        }

        // Hide the 'Download progress' link if there are
        // no active downloads or restarts pending
        if (Jenkins.get().getUpdateCenter().getJobs().isEmpty()) {
            return null;
        }

        return "symbol-list";
    }

    @Override
    public String getDisplayName() {
        return Messages.PluginsDownloadProgressLink_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "pluginManager/updates/";
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.PLUGINS;
    }
}
