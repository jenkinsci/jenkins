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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

@Extension
@Restricted(NoExternalUse.class)
public class UpdateSiteWarningsConfiguration extends GlobalConfiguration {

    private HashSet<String> ignoredWarnings = new HashSet<>();

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public UpdateSiteWarningsConfiguration() {
        load();
    }

    @Nonnull
    public Set<String> getIgnoredWarnings() {
        return Collections.unmodifiableSet(ignoredWarnings);
    }

    public boolean isIgnored(@Nonnull UpdateSite.Warning warning) {
        return ignoredWarnings.contains(warning.id);
    }

    @CheckForNull
    public PluginWrapper getPlugin(@Nonnull UpdateSite.Warning warning) {
        if (!warning.isPluginWarning()) {
            return null;
        }
        return Jenkins.getInstance().getPluginManager().getPlugin(warning.component);
    }

    @Nonnull
    public Set<UpdateSite.Warning> getApplicableWarnings() {
        HashSet<UpdateSite.Warning> allWarnings = new HashSet<>();

        for (UpdateSite site : Jenkins.getInstance().getUpdateCenter().getSites()) {
            UpdateSite.Data data = site.getData();
            if (data != null) {
                allWarnings.addAll(data.warnings);
            }
        }

        HashSet<UpdateSite.Warning> applicableWarnings = new HashSet<>();
        for (UpdateSite.Warning warning: allWarnings) {
            if (warning.isRelevant()) {
                applicableWarnings.add(warning);
            }
        }

        return Collections.unmodifiableSet(applicableWarnings);
    }


    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        HashSet<String> newIgnoredWarnings = new HashSet<>();
        for (Object key : json.keySet()) {
            String warningKey = key.toString();
            if (!json.getBoolean(warningKey)) {
                newIgnoredWarnings.add(warningKey);
            }
        }
        this.ignoredWarnings = newIgnoredWarnings;
        this.save();
        return true;
    }
}
