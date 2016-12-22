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
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Administrative monitor showing plugin/core warnings published by the configured update site to the user.
 *
 * Terminology overview:
 * - Applicable warnings are those relevant to currently installed components
 * - Active warnings are those actually shown to users.
 * - Hidden warnings are those _not_ shown to users due to them being configured to be hidden.
 * - Inapplicable warnings are those that are not applicable.
 *
 * The following sets may be non-empty:
 * - Intersection of applicable and active
 * - Intersection of applicable and hidden
 * - Intersection of hidden and inapplicable (although not really relevant)
 * - Intersection of inapplicable and neither hidden nor active
 *
 * The following sets must necessarily be empty:
 * - Intersection of applicable and inapplicable
 * - Intersection of active and hidden
 * - Intersection of active and inapplicable
 *
 */
@Extension
@Restricted(NoExternalUse.class)
public class UpdateSiteWarningsMonitor extends AdministrativeMonitor {
    @Override
    public boolean isActivated() {
        return !getActiveCoreWarnings().isEmpty() || !getActivePluginWarningsByPlugin().isEmpty();
    }

    public List<UpdateSite.Warning> getActiveCoreWarnings() {
        List<UpdateSite.Warning> CoreWarnings = new ArrayList<>();

        for (UpdateSite.Warning warning : getActiveWarnings()) {
            if (!UpdateSite.Warning.TYPE_CORE.equals(warning.type)
                    || !UpdateSite.Warning.NAME_CORE.equals(warning.name)) {
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
            if (!UpdateSite.Warning.TYPE_PLUGIN.equals(warning.type)) {
                // this is not a plugin warning
                continue;
            }

            String pluginName = warning.name;

            PluginWrapper plugin = Jenkins.getInstance().getPluginManager().getPlugin(pluginName);

            if (!activePluginWarningsByPlugin.containsKey(plugin)) {
                activePluginWarningsByPlugin.put(plugin, new ArrayList<UpdateSite.Warning>());
            }
            activePluginWarningsByPlugin.get(plugin).add(warning);
        }
        return activePluginWarningsByPlugin;

    }

    private Set<UpdateSite.Warning> getActiveWarnings() {
        ExtensionList<UpdateSiteWarningsConfiguration> configurations = ExtensionList.lookup(UpdateSiteWarningsConfiguration.class);
        if (configurations.isEmpty()) {
            return Collections.emptySet();
        }
        UpdateSiteWarningsConfiguration configuration = configurations.get(0);

        HashSet<UpdateSite.Warning> warnings = new HashSet<>(configuration.getApplicableWarnings());

        for (Iterator<UpdateSite.Warning> it = warnings.iterator(); it.hasNext(); ) {
            UpdateSite.Warning warning = it.next();

            if (configuration.getIgnoredWarnings().contains(warning.id)) {
                it.remove();
            }
        }

        return Collections.unmodifiableSet(warnings);
    }

    /**
     * Redirects the user to the security configuration where they can configure which warnings to show/hide.
     *
     * For Stapler use only.
     *
     * @param req the request
     * @param rsp the response
     * @throws IOException
     */
    public void doAct(StaplerRequest req, StaplerResponse rsp, @QueryParameter String fix, @QueryParameter String configure) throws IOException {
        if (fix != null) {
            rsp.sendRedirect(req.getContextPath() + "/pluginManager");
        }
        if (configure != null) {
            rsp.sendRedirect(req.getContextPath() + "/configureSecurity");
        }

        // shouldn't happen
        rsp.sendRedirect(req.getContextPath());
    }

    /**
     * Returns true iff there are applicable but ignored (i.e. hidden) warnings.
     *
     * @return true iff there are applicable but ignored (i.e. hidden) warnings.
     */
    public boolean hasApplicableHiddenWarnings() {
        ExtensionList<UpdateSiteWarningsConfiguration> configurations = ExtensionList.lookup(UpdateSiteWarningsConfiguration.class);
        if (configurations.isEmpty()) {
            return false;
        }

        UpdateSiteWarningsConfiguration configuration = configurations.get(0);

        return getActiveWarnings().size() < configuration.getApplicableWarnings().size();
    }

    @Override
    public String getDisplayName() {
        return "Vulnerable Plugins"; // TODO i18n
    }
}
