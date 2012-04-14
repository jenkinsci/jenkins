package hudson.model;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;
import java.util.Collection;
import java.util.Collections;

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
     * @param target for which the action objects are requested. Never null.
     * @return Can be empty but must not be null.
     */
    public Collection<? extends Action> createFor(Run target) {
        if (target instanceof AbstractBuild)
            return createFor((AbstractBuild)target);
        else
            return Collections.emptyList();
    }

    /**
     * @deprecated as of 1.461
     *      Override and call {@link #createFor(Run)} instead.
     */
    public Collection<? extends Action> createFor(AbstractBuild target) {
        return Collections.emptyList();
    }

    /**
     * Returns all the registered {@link TransientBuildActionFactory}s.
     */
    public static ExtensionList<TransientBuildActionFactory> all() {
        return Jenkins.getInstance().getExtensionList(TransientBuildActionFactory.class);
    }
}