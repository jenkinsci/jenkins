/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

package jenkins.security;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.PluginWrapper;
import hudson.model.AdministrativeMonitor;
import hudson.model.UpdateSite;
import hudson.security.Permission;
import hudson.util.HttpResponses;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Administrative monitor showing plugin/core warnings published by the configured update site to the user.
 *
 * <p>Terminology overview:</p>
 *
 * <ul>
 *   <li>Applicable warnings are those relevant to currently installed components
 *   <li>Active warnings are those actually shown to users.
 *   <li>Hidden warnings are those _not_ shown to users due to them being configured to be hidden.
 *   <li>Inapplicable warnings are those that are not applicable.
 * </ul>
 *
 * <p>The following sets may be non-empty:</p>
 *
 * <ul>
 *   <li>Intersection of applicable and active
 *   <li>Intersection of applicable and hidden
 *   <li>Intersection of hidden and inapplicable (although not really relevant)
 *   <li>Intersection of inapplicable and neither hidden nor active
 * </ul>
 *
 * <p>The following sets must necessarily be empty:</p>
 *
 * <ul>
 *   <li>Intersection of applicable and inapplicable
 *   <li>Intersection of active and hidden
 *   <li>Intersection of active and inapplicable
 * </ul>
 *
 * @since 2.40
 */
@Extension
@Restricted(NoExternalUse.class)
public class UpdateSiteWarningsMonitor extends AdministrativeMonitor {
    @Override
    public boolean isActivated() {
        if (!Jenkins.get().getUpdateCenter().isSiteDataReady()) {
            return false;
        }
        return !getActiveCoreWarnings().isEmpty() || !getActivePluginWarningsByPlugin().isEmpty();
    }

    @Override
    public boolean isSecurity() {
        return true;
    }

    public List<UpdateSite.Warning> getActiveCoreWarnings() {
        List<UpdateSite.Warning> CoreWarnings = new ArrayList<>();

        for (UpdateSite.Warning warning : getActiveWarnings()) {
            if (warning.type != UpdateSite.WarningType.CORE) {
                // this is not a core warning
                continue;
            }
            CoreWarnings.add(warning);
        }
        return CoreWarnings;
    }

    public Map<PluginWrapper, List<UpdateSite.Warning>> getActivePluginWarningsByPlugin() {
        Map<PluginWrapper, List<UpdateSite.Warning>> activePluginWarningsByPlugin = new HashMap<>();

        for (UpdateSite.Warning warning : getActiveWarnings()) {
            if (warning.type != UpdateSite.WarningType.PLUGIN) {
                // this is not a plugin warning
                continue;
            }

            String pluginName = warning.component;

            PluginWrapper plugin = Jenkins.get().getPluginManager().getPlugin(pluginName);

            if (!activePluginWarningsByPlugin.containsKey(plugin)) {
                activePluginWarningsByPlugin.put(plugin, new ArrayList<>());
            }
            activePluginWarningsByPlugin.get(plugin).add(warning);
        }
        return activePluginWarningsByPlugin;

    }

    private Set<UpdateSite.Warning> getActiveWarnings() {
        UpdateSiteWarningsConfiguration configuration = ExtensionList.lookupSingleton(UpdateSiteWarningsConfiguration.class);
        HashSet<UpdateSite.Warning> activeWarnings = new HashSet<>();

        for (UpdateSite.Warning warning : configuration.getApplicableWarnings()) {
            if (!configuration.getIgnoredWarnings().contains(warning.id)) {
                activeWarnings.add(warning);
            }
        }

        return Collections.unmodifiableSet(activeWarnings);
    }

    /**
     * Redirects the user to the plugin manager or security configuration
     */
    @RequirePOST
    public HttpResponse doForward(@QueryParameter String fix, @QueryParameter String configure) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (fix != null) {
            return HttpResponses.redirectViaContextPath("pluginManager");
        }
        if (configure != null) {
            return HttpResponses.redirectViaContextPath("configureSecurity");
        }

        // shouldn't happen
        return HttpResponses.redirectViaContextPath("/");
    }

    /**
     * Returns true iff there are applicable but ignored (i.e. hidden) warnings.
     *
     * @return true iff there are applicable but ignored (i.e. hidden) warnings.
     */
    public boolean hasApplicableHiddenWarnings() {
        UpdateSiteWarningsConfiguration configuration = ExtensionList.lookupSingleton(UpdateSiteWarningsConfiguration.class);
        return getActiveWarnings().size() < configuration.getApplicableWarnings().size();
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }

    @Override
    public String getDisplayName() {
        return Messages.UpdateSiteWarningsMonitor_DisplayName();
    }
}
