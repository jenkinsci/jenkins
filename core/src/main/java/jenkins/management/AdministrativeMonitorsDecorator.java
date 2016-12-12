/*
 * The MIT License
 *
 * Copyright (c) 2016, Daniel Beck, CloudBees, Inc.
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

import hudson.Extension;
import hudson.Functions;
import hudson.diagnosis.ReverseProxySetupMonitor;
import hudson.model.AdministrativeMonitor;
import hudson.model.PageDecorator;
import hudson.util.HttpResponses;
import hudson.util.HudsonIsLoading;
import hudson.util.HudsonIsRestarting;
import jenkins.model.Jenkins;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Show a notification and popup for active administrative monitors on all pages.
 */
@Extension
@Restricted(NoExternalUse.class)
public class AdministrativeMonitorsDecorator extends PageDecorator {
    private final Collection<String> ignoredJenkinsRestOfUrls = new ArrayList<>();

    public AdministrativeMonitorsDecorator() {
        // redundant
        ignoredJenkinsRestOfUrls.add("manage");

        // otherwise this would be added to every internal context menu building request
        ignoredJenkinsRestOfUrls.add("contextMenu");

        // don't show here to allow admins to disable malfunctioning monitors via AdministrativeMonitorsDecorator
        ignoredJenkinsRestOfUrls.add("configure");
    }

    @Override
    public String getDisplayName() {
        return Messages.AdministrativeMonitorsDecorator_DisplayName();
    }

    public int getActiveAdministrativeMonitorsCount() {
        return getActiveAdministrativeMonitors().size();
    }

    public Collection<AdministrativeMonitor> getActiveAdministrativeMonitors() {
        Collection<AdministrativeMonitor> active = new ArrayList<>();
        Collection<AdministrativeMonitor> ams = new ArrayList<>(Jenkins.getInstance().administrativeMonitors);
        for (AdministrativeMonitor am : ams) {
            if (am instanceof ReverseProxySetupMonitor) {
                // TODO make reverse proxy monitor work when shown on any URL
                continue;
            }
            if (am.isEnabled() && am.isActivated()) {
                active.add(am);
            }
        }
        return active;
    }

    /**
     * Whether the administrative monitors notifier should be shown.
     * @return true iff the administrative monitors notifier should be shown.
     * @throws IOException
     * @throws ServletException
     */
    public boolean shouldDisplay() throws IOException, ServletException {
        if (!Functions.hasPermission(Jenkins.ADMINISTER)) {
            return false;
        }

        StaplerRequest req = Stapler.getCurrentRequest();

        if (req == null) {
            return false;
        }
        List<Ancestor> ancestors = req.getAncestors();

        if (ancestors == null || ancestors.size() == 0) {
            // ???
            return false;
        }

        Ancestor a = ancestors.get(ancestors.size() - 1);
        Object o = a.getObject();

        // don't show while Jenkins is loading
        if (o instanceof HudsonIsLoading) {
            return false;
        }
        // … or restarting
        if (o instanceof HudsonIsRestarting) {
            return false;
        }

        // don't show for some URLs served directly by Jenkins
        if (o instanceof Jenkins) {
            String url = a.getRestOfUrl();

            if (ignoredJenkinsRestOfUrls.contains(url)) {
                return false;
            }
        }

        if (getActiveAdministrativeMonitorsCount() == 0) {
            return false;
        }

        return true;
    }
}