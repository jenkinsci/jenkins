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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.PluginWrapper;
import hudson.model.PersistentDescriptor;
import hudson.model.UpdateSite;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Configuration for update site-provided warnings.
 *
 * @see UpdateSiteWarningsMonitor
 *
 * @since 2.40
 */
@Extension
@Restricted(NoExternalUse.class)
public class UpdateSiteWarningsConfiguration extends GlobalConfiguration implements PersistentDescriptor {

    private HashSet<String> ignoredWarnings = new HashSet<>();

    @Override
    public @NonNull GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    @NonNull
    public Set<String> getIgnoredWarnings() {
        return Collections.unmodifiableSet(ignoredWarnings);
    }

    @DataBoundSetter // unused; for CasC support only
    public void setIgnoredWarnings(Set<String> ignoredWarnings) {
        this.ignoredWarnings = new HashSet<>(ignoredWarnings);
    }

    public boolean isIgnored(@NonNull UpdateSite.Warning warning) {
        return ignoredWarnings.contains(warning.id);
    }

    @CheckForNull
    public PluginWrapper getPlugin(@NonNull UpdateSite.Warning warning) {
        if (warning.type != UpdateSite.WarningType.PLUGIN) {
            return null;
        }
        return Jenkins.get().getPluginManager().getPlugin(warning.component);
    }

    @NonNull
    public Set<UpdateSite.Warning> getAllWarnings() {
        HashSet<UpdateSite.Warning> allWarnings = new HashSet<>();

        for (UpdateSite site : Jenkins.get().getUpdateCenter().getSites()) {
            UpdateSite.Data data = site.getData();
            if (data != null) {
                allWarnings.addAll(data.getWarnings());
            }
        }
        return allWarnings;
    }

    @NonNull
    public Set<UpdateSite.Warning> getApplicableWarnings() {
        Set<UpdateSite.Warning> allWarnings = getAllWarnings();

        HashSet<UpdateSite.Warning> applicableWarnings = new HashSet<>();
        for (UpdateSite.Warning warning : allWarnings) {
            if (warning.isRelevant()) {
                applicableWarnings.add(warning);
            }
        }

        return Collections.unmodifiableSet(applicableWarnings);
    }


    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
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
