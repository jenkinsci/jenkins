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
import hudson.Util;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import jenkins.management.AdministrativeMonitorsDecorator;
import jenkins.management.Badge;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithContextMenu;
import org.apache.commons.jelly.JellyException;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Adds the "Manage Jenkins" link to the navigation bar.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = 100) @Symbol("manageJenkins")
public class ManageJenkinsAction implements RootAction, StaplerFallback, ModelObjectWithContextMenu {
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
    public Object getStaplerFallback() {
        return Jenkins.get();
    }

    @Override
    public ContextMenu doContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws JellyException, IOException {
        return new ContextMenu().from(this, request, response, "index");
    }

    /**
     * Workaround to ensuring that links in context menus resolve correctly in the submenu of the top-level 'Dashboard'
     * menu.
     */
    @Restricted(NoExternalUse.class)
    public void addContextMenuItem(ContextMenu menu, String url, String icon, String iconXml, String text, boolean post, boolean requiresConfirmation, Badge badge, String message) {
        if (Stapler.getCurrentRequest2().findAncestorObject(this.getClass()) != null || !Util.isSafeToRedirectTo(url)) {
            // Default behavior if the URL is absolute or scheme-relative, or the current object is an ancestor (i.e. would resolve correctly)
            menu.add(url, icon, iconXml, text, post, requiresConfirmation, badge, message);
            return;
        }
        // If neither is the case, rewrite the relative URL to point to inside the /manage/ URL space
        menu.add("manage/" + url, icon, iconXml, text, post, requiresConfirmation, badge, message);
    }

    @Override
    public Badge getBadge() {
        Jenkins jenkins = Jenkins.get();
        AdministrativeMonitorsDecorator decorator = jenkins.getExtensionList(PageDecorator.class)
                .get(AdministrativeMonitorsDecorator.class);
        Collection<AdministrativeMonitor> activeAdministrativeMonitors = Optional.ofNullable(decorator.getMonitorsToDisplay()).orElse(Collections.emptyList());
        boolean anySecurity = activeAdministrativeMonitors.stream().anyMatch(AdministrativeMonitor::isSecurity);

        if (activeAdministrativeMonitors.isEmpty()) {
            return null;
        }

        // TODO - should this include plugin updates?

        int size = activeAdministrativeMonitors.size();
        String suffix = size > 1 ? "notifications" : "notification";

        return new Badge(String.valueOf(size),
                size + " " + suffix,
                anySecurity ? Badge.Severity.DANGER : Badge.Severity.WARNING);
    }
}
