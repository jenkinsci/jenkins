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

package jenkins.management;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import java.util.Set;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

@Extension
@Restricted(NoExternalUse.class)
public class AdministrativeMonitorsConfiguration extends GlobalConfiguration {

    public boolean isMonitorEnabled(String id) {
        return !Jenkins.get().getDisabledAdministrativeMonitors().contains(id);
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        JSONArray monitors = json.optJSONArray("administrativeMonitor");
        Jenkins jenkins = Jenkins.get();
        for (AdministrativeMonitor am : AdministrativeMonitor.all()) {
            boolean disable;
            if (monitors != null) {
                disable = !monitors.contains(am.id);
            } else {
                disable = !am.id.equals(json.optString("administrativeMonitor"));
            }
            Set<String> set = jenkins.getDisabledAdministrativeMonitors();
            if (disable) {
                set.add(am.id);
            } else {
                set.remove(am.id);
            }
            jenkins.setDisabledAdministrativeMonitors(set);
        }
        return true;
    }

    private static Logger LOGGER = Logger.getLogger(AdministrativeMonitorsConfiguration.class.getName());
}
