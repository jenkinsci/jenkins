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

import hudson.model.AdministrativeMonitor;
import hudson.model.AbstractModelObject;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.ExtensionList;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.List;

/**
 * Monitors the disk usage of <tt>JENKINS_HOME</tt>, and if it's almost filled up, warn the user.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public final class HudsonHomeDiskUsageMonitor extends AdministrativeMonitor {
    /**
     * Value updated by {@link HudsonHomeDiskUsageChecker}.
     */
    /*package*/ boolean activated;

    public HudsonHomeDiskUsageMonitor() {
        super("hudsonHomeIsFull");
    }

    public boolean isActivated() {
        return activated;
    }
    
    @Override
    public String getDisplayName() {
    	return Messages.HudsonHomeDiskUsageMonitor_DisplayName();
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    public HttpResponse doAct(@QueryParameter String no) throws IOException {
        if(no!=null) {
            disable(true);
            return HttpResponses.redirectViaContextPath("/manage");
        } else {
            return HttpResponses.redirectToDot();
        }
    }

    public List<Solution> getSolutions() {
        return Solution.all();
    }

    /**
     * Binds a solution to the URL.
     */
    public Solution getSolution(String id) {
        for( Solution s : Solution.all() )
            if(s.id.equals(id))
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
     *
     * <h3>Views</h3>
     * <dl>
     * <dt>message.jelly</dt>
     * <dd>
     * This view is rendered inside an LI tag as a possible solution to the full JENKINS_HOME problem.
     * </dd>
     * </dl>
     */
    public static abstract class Solution extends AbstractModelObject implements ExtensionPoint {
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
            return HudsonHomeDiskUsageMonitor.get().getUrl()+"/solution/"+id;
        }

        /**
         * All registered {@link Solution}s.
         */
        public static ExtensionList<Solution> all() {
            return ExtensionList.lookup(Solution.class);
        }
    }
}
