/*
 * The MIT License
 *
 * Copyright (c) 2016, Kohsuke Kawaguchi, Baptiste Mathus
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
package jenkins.node_monitors;

import hudson.Extension;
import hudson.model.Computer;
import hudson.node_monitors.ArchitectureMonitor;
import jenkins.model.Jenkins;
import jenkins.model.statistics.UsageStatisticsContributor;
import net.sf.json.JSONObject;

/**
 * Publishes OS information for each node of the Jenkins cluster to the usage statistics subsystem.
 *
 * @see ArchitectureMonitor
 */
@Extension
public class NodeOSUsageStatisticsContributor extends UsageStatisticsContributor {

    @Override
    public void contributeNodeData(Computer c, JSONObject n) {
        Jenkins j = Jenkins.getInstance();
        ArchitectureMonitor.DescriptorImpl descriptor = j.getDescriptorByType(ArchitectureMonitor.DescriptorImpl.class);
        n.put("os", descriptor.get(c));
    }
}
