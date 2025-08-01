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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.ExtensionPoint.LegacyInstancesAreScopedToHudson;
import hudson.security.Permission;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import java.io.IOException;
import java.util.Set;
import jenkins.model.AddressableModelObject;
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
@LegacyInstancesAreScopedToHudson
public abstract class AdministrativeMonitor extends AbstractModelObject implements AddressableModelObject, ExtensionPoint, StaplerProxy {
    /**
     * Human-readable ID of this monitor, which needs to be unique within the system.
     *
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
    @NonNull
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
        return !Jenkins.get().getDisabledAdministrativeMonitors().contains(id);
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
     * Required permission to view this admin monitor.
     * By default {@link Jenkins#ADMINISTER}, but {@link Jenkins#SYSTEM_READ} or {@link Jenkins#MANAGE} are also supported.
     * <p>
     *     Changing this permission check to return {@link Jenkins#SYSTEM_READ} will make the active
     *     administrative monitor appear on {@code manage.jelly} and on the globally visible
     *     {@link jenkins.management.AdministrativeMonitorsDecorator} to users without Administer permission.
     *     {@link #doDisable(StaplerRequest2, StaplerResponse2)} will still always require Administer permission.
     * </p>
     * <p>
     *     This method only allows for a single permission to be returned. If more complex permission checks are required,
     *     override {@link #checkRequiredPermission()} and {@link #hasRequiredPermission()} instead.
     * </p>
     * <p>
     *     Implementers need to ensure that {@code doAct} and other web methods perform necessary permission checks:
     *     Users with System Read permissions are expected to be limited to read-only access.
     *     Form UI elements that change system state, e.g. toggling a feature on or off, need to be hidden from users
     *     lacking Administer permission.
     * </p>
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
