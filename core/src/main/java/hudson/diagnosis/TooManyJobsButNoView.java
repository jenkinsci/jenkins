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
import hudson.model.AdministrativeMonitor;
import hudson.security.Permission;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * If Hudson is run with a lot of jobs but no views, suggest the user that they can create views.
 *
 * <p>
 * I noticed at an user visit that some users didn't notice the '+' icon in the tab bar.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("tooManyJobsButNoView")
public class TooManyJobsButNoView extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return Messages.TooManyJobsButNoView_DisplayName();
    }

    @Override
    public boolean isActivated() {
        Jenkins j = Jenkins.get();
        if (j.hasPermission(Jenkins.ADMINISTER)) {
            return j.getViews().size() == 1 && j.getItemMap().size() > THRESHOLD;
        }
        // SystemRead
        return j.getViews().size() == 1 && j.getItems().size() > THRESHOLD;
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @RequirePOST
    public void doAct(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (req.hasParameter("no")) {
            disable(true);
            rsp.sendRedirect(req.getContextPath() + "/manage");
        } else {
            rsp.sendRedirect(req.getContextPath() + "/newView");
        }
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }

    public static final int THRESHOLD = 16;
}
