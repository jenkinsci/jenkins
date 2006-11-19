package hudson.triggers;

import hudson.model.Descriptor;

import java.util.List;

/**
 * List of all installed {@link Trigger}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class Triggers {
    public static final List<Descriptor<Trigger>> TRIGGERS = Descriptor.toList(
        SCMTrigger.DESCRIPTOR,
        TimerTrigger.DESCRIPTOR
    );
}
