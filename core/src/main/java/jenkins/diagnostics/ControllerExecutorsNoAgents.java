/*
 * The MIT License
 *
 * Copyright (c) 2021, CloudBees, Inc.
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

package jenkins.diagnostics;

import hudson.Extension;
import hudson.Main;
import hudson.model.AdministrativeMonitor;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
@Symbol({"executorsOnBuiltInNodeWithoutAgents", "controllerExecutorsWithoutAgents"})
@Restricted(NoExternalUse.class)
public class ControllerExecutorsNoAgents extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return Messages.ControllerExecutorsNoAgents_DisplayName();
    }

    @Override
    public boolean isSecurity() {
        return true;
    }

    @RequirePOST
    public void doAct(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (req.hasParameter("no")) {
            disable(true);
            rsp.sendRedirect(req.getContextPath() + "/manage");
        } else if (req.hasParameter("cloud")) {
            rsp.sendRedirect(req.getContextPath() + "/manage/cloud/");
        } else if (req.hasParameter("agent")) {
            rsp.sendRedirect(req.getContextPath() + "/computer/new");
        }
    }

    @Override
    public boolean isActivated() {
        return !Main.isDevelopmentMode && Jenkins.get().getNumExecutors() > 0 &&
                Jenkins.get().clouds.isEmpty() && Jenkins.get().getNodes().isEmpty();
    }

}
