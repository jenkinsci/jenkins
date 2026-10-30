/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Intl., Nicolas De loof
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

package jenkins.management;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import hudson.util.HudsonIsLoading;
import hudson.util.HudsonIsRestarting;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension(ordinal = Integer.MAX_VALUE - 200) @Symbol("configure")
public class ConfigureLink extends ManagementLink {

    private static final Logger LOGGER = Logger.getLogger(ConfigureLink.class.getName());

    @Override
    public String getIconFileName() {
        return "symbol-settings";
    }

    @Override
    public String getDisplayName() {
        return Messages.ConfigureLink_DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.ConfigureLink_Description();
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.READ;
    }

    @Override
    public boolean hasRequiredPermission() {
        return Jenkins.get().hasAnyPermission(Jenkins.MANAGE_AND_SYSTEM_READ);
    }

    @Override
    public String getUrlName() {
        return "configure";
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @Override
    public Badge getBadge() {
        if (!AdministrativeMonitor.hasPermissionToDisplay()) {
            return null;
        }

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }

        var app = jenkins.getServletContext() != null ? jenkins.getServletContext().getAttribute("app") : null;
        if (app instanceof HudsonIsLoading || app instanceof HudsonIsRestarting) {
            return null;
        }

        List<AdministrativeMonitor> activeMonitors = jenkins.administrativeMonitors.stream()
                .filter(ConfigureLink::isActive)
                .toList();

        int size = activeMonitors.size();
        if (size > 0) {
            int securityCount = (int) activeMonitors.stream().filter(AdministrativeMonitor::isSecurity).count();
            Badge.Severity severity = securityCount > 0 ? Badge.Severity.DANGER : Badge.Severity.WARNING;

            StringBuilder tooltip = new StringBuilder();
            if (size == 1) {
                tooltip.append(Messages.ConfigureLink_notificationAvailable());
            } else {
                tooltip.append(Messages.ConfigureLink_notificationsAvailable(size));
            }

            if (securityCount == 1) {
                tooltip.append("\n").append(Messages.ConfigureLink_securityNotificationAvailable());
            } else if (securityCount > 1) {
                tooltip.append("\n").append(Messages.ConfigureLink_securityNotificationsAvailable(securityCount));
            }

            return new Badge(String.valueOf(size), tooltip.toString(), severity);
        }
        return null;
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
