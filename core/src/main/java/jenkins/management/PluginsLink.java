/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Intl., Nicolas De loof
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
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite.Plugin;
import hudson.security.Permission;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension(ordinal = Integer.MAX_VALUE - 400) @Symbol("plugins")
public class PluginsLink extends ManagementLink {

    @Override
    public String getIconFileName() {
        return "plugin.svg";
    }

    @Override
    public String getDisplayName() {
        return Messages.PluginsLink_DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.PluginsLink_Description();
    }

    @Override
    public String getUrlName() {
        return "pluginManager";
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @Override
    public Badge getBadge() {
        final UpdateCenter updateCenter = Jenkins.get().getUpdateCenter();
        if (!updateCenter.isSiteDataReady()) {
            // Do not display message during this page load, but possibly later.
            return null;
        }
        List<Plugin> plugins = updateCenter.getUpdates();
        int size = plugins.size();
        if (size > 0) {
            StringBuilder tooltip = new StringBuilder();
            Badge.Severity severity = Badge.Severity.WARNING;
            int securityFixSize = (int) plugins.stream().filter(plugin -> plugin.fixesSecurityVulnerabilities()).count();
            int incompatibleSize = (int) plugins.stream().filter(plugin -> !plugin.isCompatibleWithInstalledVersion()).count();
            if (size > 1) {
                tooltip.append(Messages.PluginsLink_updatesAvailable(size));
            } else {
                tooltip.append(Messages.PluginsLink_updateAvailable());
            }
            switch (incompatibleSize) {
                case 0:
                    break;
                case 1:
                    tooltip.append("\n").append(Messages.PluginsLink_incompatibleUpdateAvailable());
                    break;
                default:
                    tooltip.append("\n").append(Messages.PluginsLink_incompatibleUpdatesAvailable(incompatibleSize));
                    break;
            }
            switch (securityFixSize) {
                case 0:
                    break;
                case 1:
                    tooltip.append("\n").append(Messages.PluginsLink_securityUpdateAvailable());
                    severity = Badge.Severity.DANGER;
                    break;
                default:
                    tooltip.append("\n").append(Messages.PluginsLink_securityUpdatesAvailable(securityFixSize));
                    severity = Badge.Severity.DANGER;
                    break;
            }
            return new Badge(Integer.toString(size), tooltip.toString(), severity);
        }
        return null;
    }
}
