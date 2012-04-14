package hudson.model;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;
import java.util.Collection;

/**
 * Extension point for inserting transient {@link Action}s into {@link Run}s.
 *
 * To register your implementation, put {@link Extension} on your subtype.
 *
 * @author Lucie Votypkova
 * @since 1.458
 * @see Action
 */

public abstract class TransientBuildActionFactory implements ExtensionPoint {
    /**
     * Creates actions for the given build.
     *
     * @param Build for which the action objects are requested. Never null.
     * @return Can be empty but must not be null.
     */
    public abstract Collection<? extends Action> createFor(Run target);

    /**
     * Returns all the registered {@link TransientBuildActionFactory}s.
     */
    public static ExtensionList<TransientBuildActionFactory> all() {
        return Jenkins.getInstance().getExtensionList(TransientBuildActionFactory.class);
    }
}