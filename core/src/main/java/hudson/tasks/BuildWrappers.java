package hudson.tasks;

import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Project;

import java.util.ArrayList;
import java.util.List;

/**
 * List of all installed {@link BuildWrapper}.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildWrappers {
    public static final List<Descriptor<BuildWrapper>> WRAPPERS = Descriptor.toList(
    );

    /**
     * List up all {@link BuildWrapperDescriptor}s that are applicable for the given project.
     *
     * @return
     *      The signature doesn't use {@link BuildWrapperDescriptor} to maintain compatibility
     *      with {@link BuildWrapper} implementations before 1.150.
     */
    public static List<Descriptor<BuildWrapper>> getFor(AbstractProject<?, ?> project) {
        List<Descriptor<BuildWrapper>> result = new ArrayList<Descriptor<BuildWrapper>>();
        for (Descriptor<BuildWrapper> w : WRAPPERS) {
            if (w instanceof BuildWrapperDescriptor) {
                BuildWrapperDescriptor bwd = (BuildWrapperDescriptor) w;
                if(bwd.isApplicable(project))
                    result.add(bwd);
            } else {
                // old BuildWrapper that doesn't implement BuildWrapperDescriptor
                result.add(w);
            }
        }
        return result;
    }
}
