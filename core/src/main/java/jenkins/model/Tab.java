package jenkins.model;

import hudson.model.Action;
import hudson.model.Actionable;

/**
 * Represents a tab element shown on {@link Actionable} views.
 * <p>
 * A {@code Tab} is an {@link Action} that can be attached to an {@link Actionable} object
 * (such as a job or build) and displayed as a separate tab in the UI.
 * </p>
 *
 * <p>
 * Tabs may also implement {@link Badgeable} to display a visual badge associated
 * with the tab’s action
 * </p>
 * @since TODO
 */
public abstract class Tab implements Action, Badgeable {

    protected Actionable object;

    public Tab(Actionable object) {
        this.object = object;
    }

    public Actionable getObject() {
        return object;
    }
}
