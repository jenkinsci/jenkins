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
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.UpdateSite;
import hudson.util.VersionNumber;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Extension
@Restricted(NoExternalUse.class)
public class UpdateSiteWarningsConfiguration extends GlobalConfiguration {

    private HashSet<String> ignoredWarnings = new HashSet<>();

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    @DataBoundConstructor
    public UpdateSiteWarningsConfiguration() {
        load();
    }

    public void ignore(String warningId) {
        ignoredWarnings.add(warningId);
        save();
    }

    public Set<String> getIgnoredWarnings() {
        return Collections.unmodifiableSet(ignoredWarnings);
    }

    public boolean isIgnored(UpdateSite.Warning warning) {
        return getIgnoredWarnings().contains(warning.id);
    }

    public PluginWrapper getPlugin(UpdateSite.Warning warning) {
        if (!UpdateSite.Warning.TYPE_PLUGIN.equals(warning.type)) {
            return null;
        }
        return Jenkins.getInstance().getPluginManager().getPlugin(warning.name);
    }

    public Set<UpdateSite.Warning> getApplicableWarnings() {
        HashSet<UpdateSite.Warning> warnings = new HashSet<>();

        for (UpdateSite site : Jenkins.getInstance().getUpdateCenter().getSites()) {
            UpdateSite.Data data = site.getData();
            if (data != null) {
                warnings.addAll(data.warnings);
            }
        }

        PluginManager pluginManager = Jenkins.getInstance().getPluginManager();

        for (Iterator<UpdateSite.Warning> it = warnings.iterator(); it.hasNext(); ) {
            UpdateSite.Warning warning = it.next();

            if (UpdateSite.Warning.TYPE_PLUGIN.equals(warning.type)) {
                PluginWrapper plugin = pluginManager.getPlugin(warning.name);

                if (plugin == null) {
                    // it's a warning for a plugin that's not installed
                    it.remove();
                    continue;
                }

                // check whether warning is relevant to installed version
                VersionNumber current = plugin.getVersionNumber();
                if (!isWarningRelevantToInstalledVersion(warning, current)) {
                    it.remove();
                }
            }

            if (UpdateSite.Warning.TYPE_CORE.equals(warning.type)
                    && UpdateSite.Warning.NAME_CORE.equals(warning.name)) {

                VersionNumber current = Jenkins.getVersion();

                if (!isWarningRelevantToInstalledVersion(warning, current)) {
                    it.remove();
                }
            }
        }

        return Collections.unmodifiableSet(warnings);
    }

    private boolean isWarningRelevantToInstalledVersion(UpdateSite.Warning warning, VersionNumber version) {
        if (warning.versionRanges.isEmpty()) {
            // no version ranges specified, so all versions are affected
            return true;
        }

        for (UpdateSite.WarningVersionRange range : warning.versionRanges) {
            if (range.pattern.matcher(version.toString()).matches()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        ignoredWarnings.clear();
        if (json.has("ignoredWarnings")) {
            JSONArray warnings = json.optJSONArray("ignoredWarnings");
            if (warnings != null) {
                for (int i = 0; i < warnings.size(); i++) {
                    ignore(warnings.getString(i));
                }
            } else {
                // single item variant of a list thing
                ignore(json.getString("ignoredWarnings"));
            }
        }
        return true;
    }
}
