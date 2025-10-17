/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

package hudson.diagnosis;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractModelObject;
import hudson.model.AdministrativeMonitor;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Monitors the disk usage of {@code JENKINS_HOME}, and if it's almost filled up, warn the user.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("diskUsageCheck")
public final class HudsonHomeDiskUsageMonitor extends AdministrativeMonitor {
    /**
     * Value updated by {@link HudsonHomeDiskUsageChecker}.
     */
    /*package*/ boolean activated;

    public HudsonHomeDiskUsageMonitor() {
        super("hudsonHomeIsFull");
    }

    @Override
    public boolean isActivated() {
        return activated;
    }

    @Override
    public String getDisplayName() {
        return Messages.HudsonHomeDiskUsageMonitor_DisplayName();
    }

    @RequirePOST
    public HttpResponse doTellMeMore() {
        return HttpResponses.redirectToDot();
    }

    public List<Solution> getSolutions() {
        return Solution.all();
    }

    /**
     * Binds a solution to the URL.
     */
    public Solution getSolution(String id) {
        for (Solution s : Solution.all())
            if (s.id.equals(id))
                return s;
        return null;
    }

    /**
     * Short cut for getting the singleton instance.
     */
    public static HudsonHomeDiskUsageMonitor get() {
        return all().get(HudsonHomeDiskUsageMonitor.class);
    }

    /**
     * Extension point for suggesting solutions for full JENKINS_HOME.
     * Views are as follows:
     *
     * <dl>
     * <dt>message.jelly</dt>
     * <dd>
     * This view is rendered inside an LI tag as a possible solution to the full JENKINS_HOME problem.
     * </dd>
     * </dl>
     */
    public abstract static class Solution extends AbstractModelObject implements ExtensionPoint {
        /**
         * Human-readable ID of this monitor, which needs to be unique within the system.
         *
         * <p>
         * This ID is used to remember persisted setting for this monitor,
         * so the ID should remain consistent beyond the Hudson JVM lifespan.
         */
        public final String id;

        protected Solution(String id) {
            this.id = id;
        }

        protected Solution() {
            this.id = this.getClass().getName();
        }

        /**
         * Returns the URL of this monitor, relative to the context path.
         */
        public String getUrl() {
            return HudsonHomeDiskUsageMonitor.get().getUrl() + "/solution/" + id;
        }

        /**
         * All registered {@link Solution}s.
         */
        public static ExtensionList<Solution> all() {
            return ExtensionList.lookup(Solution.class);
        }
    }
}
