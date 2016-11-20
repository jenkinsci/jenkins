/*
 * The MIT License
 *
 * Copyright (c) 2004-2016, Sun Microsystems, Inc., Kohsuke Kawaguchi, Baptiste Mathus
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
package jenkins.model.statistics;

import java.util.ArrayList;
import java.util.List;

import hudson.Extension;
import hudson.PluginWrapper;
import hudson.model.Items;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import jenkins.model.Jenkins;
import jenkins.model.statistics.UsageStatisticsContributor;
import net.sf.json.JSONObject;

@Extension
public class CoreUsageStatistics extends UsageStatisticsContributor {

    @Override
    public void contributeData(JSONObject o) {
        Jenkins j = Jenkins.getInstance();
        o.put("stat", 1);
        o.put("install", j.getLegacyInstanceId());
        o.put("servletContainer", j.servletContext.getServerInfo());
        o.put("version", Jenkins.VERSION);

        List<JSONObject> plugins = new ArrayList<JSONObject>();
        for (PluginWrapper pw : j.getPluginManager().getPlugins()) {
            if (!pw.isActive())
                continue; // treat disabled plugins as if they are uninstalled
            JSONObject p = new JSONObject();
            p.put("name", pw.getShortName());
            p.put("version", pw.getVersion());
            plugins.add(p);
        }
        o.put("plugins", plugins);

        JSONObject jobs = new JSONObject();
        List<TopLevelItem> items = j.getAllItems(TopLevelItem.class);
        for (TopLevelItemDescriptor d : Items.all()) {
            int cnt = 0;
            for (TopLevelItem item : items) {
                if (item.getDescriptor() == d)
                    cnt++;
            }
            jobs.put(d.getJsonSafeClassName(), cnt);
        }
        o.put("jobs", jobs);
    }
}
