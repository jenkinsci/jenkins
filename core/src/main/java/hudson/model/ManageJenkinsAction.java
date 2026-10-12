/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

package hudson.model;

import hudson.Extension;
import hudson.util.HudsonIsLoading;
import hudson.util.HudsonIsRestarting;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.management.Badge;
import jenkins.model.Jenkins;
import jenkins.model.experimentalflags.NewManageJenkinsUserExperimentalFlag;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Adds the "Manage Jenkins" link to the navigation bar.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = 998) @Symbol("manageJenkins")
public class ManageJenkinsAction implements RootAction, StaplerFallback {

    private static final Logger LOGGER = Logger.getLogger(ManageJenkinsAction.class.getName());

    @Override
    public String getIconFileName() {
        if (Jenkins.get().hasAnyPermission(Jenkins.MANAGE, Jenkins.SYSTEM_READ))
            return "symbol-settings";
        else
            return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.ManageJenkinsAction_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "/manage";
    }

    @Override
    public boolean isPrimaryAction() {
        return true;
    }

    public HttpRedirect doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        try {
            var newUiEnabled = new NewManageJenkinsUserExperimentalFlag().getFlagValue();

            if (newUiEnabled) {
                return new HttpRedirect("configure");
            }

            req.getView(this, "index.jelly").forward(req, rsp);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public Object getStaplerFallback() {
        return Jenkins.get();
    }

    /** Unlike {@link Jenkins#getActiveAdministrativeMonitors} this checks for activation lazily. */
    @Override
    public Badge getBadge() {
        if (!(AdministrativeMonitor.hasPermissionToDisplay())) {
            return null;
        }

        var app = Jenkins.get().getServletContext().getAttribute("app");
        if (app instanceof HudsonIsLoading || app instanceof HudsonIsRestarting) {
            return null;
        }

        if (Jenkins.get().administrativeMonitors.stream().anyMatch(m -> m.isSecurity() && isActive(m))) {
            return new Badge("1+", Messages.ManageJenkinsAction_notifications(),
                    Badge.Severity.DANGER);
        } else if (Jenkins.get().administrativeMonitors.stream().anyMatch(m -> !m.isSecurity() && isActive(m))) {
            return new Badge("1+", Messages.ManageJenkinsAction_notifications(),
                    Badge.Severity.WARNING);
        } else {
            return null;
        }
    }

    private static boolean isActive(AdministrativeMonitor m) {
        try {
            return !m.isActivationFake() && m.hasRequiredPermission() && m.isEnabled() && m.isActivated();
        } catch (Throwable x) {
            LOGGER.log(Level.WARNING, null, x);
            return false;
        }
    }

}
