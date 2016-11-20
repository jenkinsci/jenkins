/*
 * The MIT License
 *
 * Copyright (c) 2016, Baptiste Mathus
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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Computer;
import hudson.model.UsageStatistics;
import net.sf.json.JSONObject;

import javax.annotation.Nonnull;

/**
 * <p>
 * Extension point to contribute usage statistics to the {@link UsageStatistics} class and associated infrastructure
 * (see below).
 * </p>
 * <p/>
 * <p>
 * When adding a new implementation:
 * </p>
 * <ul>
 * <li>You'll have to document the requirement at the
 * <a href="https://github.com/jenkinsci/infra-statistics">infra-statistics</a> project so that your data can be
 * processed ;</li>
 * <li><strong>WARNING:</strong> Because there can be security/privacy implications, before adding a new implementation
 * of this extension point in your plugin, it is <strong>strongly recommended</strong> to get your code reviewed by the
 * community in general, and by the
 * <a href="https://wiki.jenkins-ci.org/display/JENKINS/Governance+Board#GovernanceBoard-Security">Jenkins Security
 * Officer</a> in particular.</li>
 * </ul>
 * <p/>
 */
public abstract class UsageStatisticsContributor implements ExtensionPoint {

    /**
     * Every {@link UsageStatisticsContributor} instances.
     *
     * @return all the {@link UsageStatisticsContributor} instances.
     */
    public static ExtensionList<UsageStatisticsContributor> all() {
        return ExtensionList.lookup(UsageStatisticsContributor.class);
    }

    /**
     * Gets the information to be contributed to the usage statistics subsystem.
     *
     * @param data the data to enrich.
     */
    public void contributeData(@Nonnull JSONObject data) {
    }

    /**
     * Called for each {@link Computer} of the Jenkins cluster.
     *
     * @param computer the current machine.
     * @param nodeData the data to enrich.
     */
    public void contributeNodeData(@Nonnull Computer computer, @Nonnull JSONObject nodeData) {
    }
}
