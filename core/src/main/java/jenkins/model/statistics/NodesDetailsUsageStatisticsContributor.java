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
package jenkins.model.statistics;

import hudson.Extension;
import hudson.model.Computer;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * Publishes informations about the used VM, number of executors and so on
 * for each node of the Jenkins cluster to the usage statistics subsystem.
 */
@Extension
public class NodesDetailsUsageStatisticsContributor extends UsageStatisticsContributor {

    @Override
    public void contributeNodeData(Computer c, JSONObject n) {
        Jenkins j = Jenkins.getInstance();
        if (c.getNode() == j) {
            n.put("master", true);
            n.put("jvm-vendor", System.getProperty("java.vm.vendor"));
            n.put("jvm-name", System.getProperty("java.vm.name"));
            n.put("jvm-version", System.getProperty("java.version"));
        }
        n.put("executors", c.getNumExecutors());
    }
}
