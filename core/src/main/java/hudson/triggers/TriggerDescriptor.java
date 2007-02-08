package hudson.triggers;

import hudson.model.Descriptor;
import hudson.model.Item;

/**
 * {@link Descriptor} for Trigger.
 * @author Kohsuke Kawaguchi
 */
public abstract class TriggerDescriptor extends Descriptor<Trigger<?>> {
    protected TriggerDescriptor(Class<? extends Trigger<?>> clazz) {
        super(clazz);
    }

    /**
     * Returns true if this trigger is applicable to the
     * given {@link Item}.
     *
     * @return
     *      true to allow user to configure a trigger for this item.
     */
    public abstract boolean isApplicable(Item item);
}
