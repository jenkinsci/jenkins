package hudson.triggers;

import hudson.model.Descriptor;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class Triggers {
    /**
     * List of all installed {@link Trigger}s.
     */
    public static final List<Descriptor<Trigger>> TRIGGERS = Descriptor.toList(
        SCMTrigger.DESCRIPTOR,
        TimerTrigger.DESCRIPTOR
    );
}
