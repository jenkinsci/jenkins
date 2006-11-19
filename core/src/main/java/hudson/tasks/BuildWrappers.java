package hudson.tasks;

import hudson.model.Descriptor;

import java.util.List;

/**
 * List of all installed {@link BuildWrapper}.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildWrappers {
    public static final List<Descriptor<BuildWrapper>> WRAPPERS = Descriptor.toList(
    );
}
