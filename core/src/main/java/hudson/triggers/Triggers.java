package hudson.triggers;

import hudson.model.Descriptor;
import hudson.model.Item;

import java.util.List;
import java.util.ArrayList;

/**
 * List of all installed {@link Trigger}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class Triggers {
    public static final List<TriggerDescriptor> TRIGGERS = Descriptor.toList(
        SCMTrigger.DESCRIPTOR,
        TimerTrigger.DESCRIPTOR
    );

    /**
     * Returns a subset of {@link TriggerDescriptor}s that applys to the given item.
     */
    public static List<TriggerDescriptor> getApplicableTriggers(Item i) {
        List<TriggerDescriptor> r = new ArrayList<TriggerDescriptor>();
        for (TriggerDescriptor t : TRIGGERS) {
            if(t.isApplicable(i))
                r.add(t);
        }
        return r;
    }
}
