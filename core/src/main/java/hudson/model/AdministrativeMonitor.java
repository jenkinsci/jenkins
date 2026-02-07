/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.security.Permission;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Checks the health of a subsystem of Jenkins and if there's something
 * that requires administrator's attention, notify the administrator.
 *
 * <h2>How to implement?</h2>
 * <p>
 * Plugins who wish to contribute such notifications can implement this
 * class and put {@link Extension} on it to register it to Jenkins.
 *
 * <p>
 * Once installed, it's the implementer's responsibility to perform
 * monitoring and activate/deactivate the monitor accordingly. Sometimes
 * this can be done by updating a flag from code (see {@link SCMTrigger}
 * for one such example), while other times it's more convenient to do
 * so by running some code periodically (for this, use {@link TimerTrigger#timer})
 *
 * <p>
 * {@link AdministrativeMonitor}s are bound to URL by {@link Jenkins#getAdministrativeMonitor(String)}.
 * See {@link #getUrl()}.
 *
 * <h3>Views</h3>
 * <dl>
 * <dt>message.jelly</dt>
 * <dd>
 * If {@link #isActivated()} returns true, Jenkins will use the {@code message.jelly}
 * view of this object to render the warning text. This happens in the
 * {@code http://SERVER/jenkins/manage} page. This view should typically render
 * a DIV box with class='alert alert-danger' or class='alert alert-warning' with a human-readable text
 * inside it. It often also contains a link to a page that provides more details
 * about the problem.<br>
 * Additionally 2 numbers are shown in the Jenkins header of administrators, one with the number or active
 * non-security relevant monitors and one with the number of active security relevant monitors.
 * </dd>
 * </dl>
 *
 * <h3>Use with System Read permission</h3>
 * <p>
 *     By default administrative monitors are visible only to users with Administer permission.
 *     Users with {@link Jenkins#SYSTEM_READ} permission can access administrative monitors that override {@link #getRequiredPermission()}.
 *     Care needs to be taken to ensure users with that permission don't have access to actions modifying system state.
 *     For more details, see {@link #getRequiredPermission()}.
 * </p>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.273
 * @see Jenkins#administrativeMonitors
 */
public abstract class AdministrativeMonitor extends AbstractModelObject implements ExtensionPoint, StaplerProxy {
    private static final Logger LOGGER = Logger.getLogger(AdministrativeMonitor.class.getName());

    /**
     * Human-readable ID of this monitor, which needs to be unique within the system.
     * <p>
     * This ID is used to remember persisted setting for this monitor,
     * so the ID should remain consistent beyond the Hudson JVM lifespan.
     */
    public final String id;

    protected AdministrativeMonitor(String id) {
        this.id = id;
    }

    protected AdministrativeMonitor() {
        this.id = this.getClass().getName();
    }

    /**
     * Returns the URL of this monitor, relative to the context path, like "administrativeMonitor/foobar".
     */
    public String getUrl() {
        return "administrativeMonitor/" + id;
    }

    @Override
    public String getDisplayName() {
        return id;
    }

    @Override
    public final String getSearchUrl() {
        return getUrl();
    }

    /**
     * Mark this monitor as disabled, to prevent this from showing up in the UI.
     */
    public void disable(boolean value) throws IOException {
        AbstractCIBase jenkins = Jenkins.get();
        Set<String> set = jenkins.getDisabledAdministrativeMonitors();
        if (value) {
            set.add(id);
        } else {
            set.remove(id);
        }
        jenkins.setDisabledAdministrativeMonitors(set);
        jenkins.save();
    }

    /**
     * Returns true if this monitor {@link #disable(boolean) isn't disabled} earlier.
     *
     * <p>
     * This flag implements the ability for the admin to say "no thank you" to the monitor that 
     * he wants to ignore.
     */
    public boolean isEnabled() {
        if (isSnoozed()) {
            return false;
        }
        return !Jenkins.get().getDisabledAdministrativeMonitors().contains(id);
    }

    /**
     * @since TODO
     */
    public boolean isSnoozed() {
        Map<String, Long> snoozed = Jenkins.get().getSnoozedAdministrativeMonitors();
        Long expiry = snoozed.get(id);
        if (expiry == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now >= expiry) {
            // Cleanup expired entry to prevent memory leak
            try {
                AbstractCIBase jenkins = Jenkins.get();
                Map<String, Long> map = jenkins.getSnoozedAdministrativeMonitors();
                if (map.remove(id) != null) {
                    jenkins.setSnoozedAdministrativeMonitors(map);
                    jenkins.save();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to cleanup expired snooze for " + id, e);
            }
            return false;
        }
        return true;
    }

    /**
     * @since TODO
     */
    public void snooze(long durationMs) throws IOException {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        if (durationMs > 365L * 24 * 60 * 60 * 1000) {
            throw new IllegalArgumentException("Duration exceeds maximum (1 year)");
        }
        long expiryTime = System.currentTimeMillis() + durationMs;
        AbstractCIBase jenkins = Jenkins.get();
        Map<String, Long> map = jenkins.getSnoozedAdministrativeMonitors();
        map.put(id, expiryTime);
        jenkins.setSnoozedAdministrativeMonitors(map);
        jenkins.save();
    }

    /**
     * Returns true if this monitor is activated and
     * wants to produce a warning message.
     *
     * <p>
     * This method is called from the HTML rendering thread,
     * so it should run efficiently.
     */
    public abstract boolean isActivated();

    @Restricted(NoExternalUse.class)
    public boolean isActivationFake() {
        return false;
    }

    /**
     * Returns true if this monitor is security related.
     *
     * This will be used to determine which icon will be used in the navigation bar.
     *
     * @since 2.267
     */
    public boolean isSecurity() {
        return false;
    }

    /**
     * URL binding to disable this monitor.
     */
    @RequirePOST
    public void doDisable(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        disable(true);
        rsp.sendRedirect2(req.getContextPath() + "/manage");
    }

    /**
     * @since TODO
     */
    @RequirePOST
    public void doSnooze(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        long durationMs;

        try {
            String preset = req.getParameter("durationPreset");
            if (preset == null) {
                throw new IllegalArgumentException("No duration specified");
            }

            if ("custom".equals(preset)) {
                String snoozeUntilStr = req.getParameter("snoozeUntil");
                if (snoozeUntilStr == null || snoozeUntilStr.isEmpty()) {
                    throw new IllegalArgumentException("Custom date required");
                }
                // Parse date in format yyyy-MM-dd
                java.time.LocalDate snoozeDate = java.time.LocalDate.parse(snoozeUntilStr);
                java.time.LocalDate today = java.time.LocalDate.now();
                if (!snoozeDate.isAfter(today)) {
                    throw new IllegalArgumentException("Snooze date must be in the future");
                }
                // Calculate milliseconds until end of that day
                java.time.ZonedDateTime endOfDay = snoozeDate.atStartOfDay(java.time.ZoneId.systemDefault())
                        .plusDays(1);
                durationMs = endOfDay.toInstant().toEpochMilli() - System.currentTimeMillis();
            } else {
                long minutes = Long.parseLong(preset);
                if (minutes <= 0) {
                    throw new IllegalArgumentException("Invalid preset duration");
                }
                durationMs = minutes * 60 * 1000;
            }

            // Validate max duration (1 year)
            if (durationMs > 365L * 24 * 60 * 60 * 1000) {
                throw new IllegalArgumentException("Duration exceeds maximum (1 year)");
            }

        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            rsp.sendError(StaplerResponse2.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        snooze(durationMs);

        // AJAX: return 200; otherwise redirect
        if ("XMLHttpRequest".equals(req.getHeader("X-Requested-With"))) {
            rsp.setStatus(StaplerResponse2.SC_OK);
        } else {
            rsp.sendRedirect2(req.getContextPath() + "/manage");
        }
    }

    /**
     * Required permission to view this admin monitor.
     * By default {@link Jenkins#ADMINISTER}, but {@link Jenkins#SYSTEM_READ} or {@link Jenkins#MANAGE} are also supported.
     * <p>
     *     Changing this permission check to return {@link Jenkins#SYSTEM_READ} will make the active
     *     administrative monitor appear on {@link ManageJenkinsAction} to users without Administer permission.
     *     {@link #doDisable(StaplerRequest2, StaplerResponse2)} will still always require Administer permission.
     * </p>
     * <p>
     *     This method only allows for a single permission to be returned. If more complex permission checks are required,
     *     override {@link #checkRequiredPermission()} and {@link #hasRequiredPermission()} instead.
     * </p>
     * <p>
     * Implementers need to ensure that {@code doAct} and other web methods perform necessary permission checks:
     * Users with System Read permissions are expected to be limited to read-only access.
     * Form UI elements that change system state, e.g. toggling a feature on or off, need to be hidden from users
     * lacking Administer permission.
     * </p>
     *
     * @since 2.233
     * @deprecated Callers should use {@link #checkRequiredPermission()} or {@link #hasRequiredPermission()}.
     */
    @Deprecated
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    /**
     * Checks if the current user has the minimum required permission to view this administrative monitor.
     * <p>
     * Subclasses may override this method and {@link #hasRequiredPermission()} instead of {@link #getRequiredPermission()} to perform more complex permission checks,
     * for example, checking either {@link Jenkins#MANAGE} or {@link Jenkins#SYSTEM_READ}.
     * </p>
     * @see #getRequiredPermission()
     * @see #hasRequiredPermission()
     * @since 2.468
     */
    public void checkRequiredPermission() {
        Jenkins.get().checkPermission(getRequiredPermission());
    }

    /**
     * Checks if the current user has the minimum required permission to view this administrative monitor.
     * <p>
     * Subclasses may override this method and {@link #checkRequiredPermission} instead of {@link #getRequiredPermission()} to perform more complex permission checks,
     * for example, checking either {@link Jenkins#MANAGE} or {@link Jenkins#SYSTEM_READ}.
     * </p>
     * @see #getRequiredPermission()
     * @see #checkRequiredPermission()
     * @since 2.468
     */
    public boolean hasRequiredPermission() {
        return Jenkins.get().hasPermission(getRequiredPermission());
    }

    /**
     * Checks if the current user has the minimum required permission to view any administrative monitor.
     *
     * @return true if the current user has the minimum required permission to view any administrative monitor.
     *
     * @since 2.468
     */
    public static boolean hasPermissionToDisplay() {
        return Jenkins.get().hasAnyPermission(Jenkins.SYSTEM_READ, Jenkins.MANAGE);
    }

    /**
     * Ensure that URLs in this administrative monitor are only accessible to users with {@link #getRequiredPermission()}.
     */
    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        checkRequiredPermission();
        return this;
    }

    /**
     * All registered {@link AdministrativeMonitor} instances.
     */
    public static ExtensionList<AdministrativeMonitor> all() {
        return ExtensionList.lookup(AdministrativeMonitor.class);
    }
}
