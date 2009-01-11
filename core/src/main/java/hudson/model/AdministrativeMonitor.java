package hudson.model;

import hudson.ExtensionPoint;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;

/**
 * Checks the health of a subsystem of Hudson and if there's something
 * that requires administrator's attention, notify the administrator.
 *
 * <h2>How to implement?</h2>
 * <p>
 * Plugins who wish to contribute such notifications can implement this
 * class and add to {@link Hudson#administrativeMonitors} to register
 * the object to Hudson.
 *
 * <p>
 * Once installed, it's the implementor's responsibility to perform
 * monitoring and activate/deactivate the monitor accordingly. Sometimes
 * this can be done by updating a flag from code (see {@link SCMTrigger}
 * for one such example), while other times it's more convenient to do
 * so by running some code periodically (for this, use {@link TimerTrigger#timer})
 *
 * <h3>Views</h3>
 * <dl>
 * <dt>message.jelly</dt>
 * <dd>
 * If {@link #isActivated()} returns true, Hudson will use the <tt>message.jelly</tt>
 * view of this object to render the warning text. This happens in the
 * <tt>http://SERVER/hudson/manage</tt> page. This view should typically render
 * a DIV box with class='error' or class='warning' with a human-readable text
 * inside it. It often also contains a link to a page that provides more details
 * about the problem.
 * </dd>
 * </dl>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.273
 * @see Hudson#administrativeMonitors
 */
public abstract class AdministrativeMonitor implements ExtensionPoint {
    /**
     * Human-readable ID of this monitor, which needs to be unique within the system.
     *
     * <p>
     * In the future we intend to have some persistable user preference about
     * monitors (such as disabling it or configuring it for e-mail notification),
     * so the ID should remain consistent beyond the Hudson JVM lifespan.
     */
    public final String id;

    protected AdministrativeMonitor(String id) {
        this.id = id;
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
}
