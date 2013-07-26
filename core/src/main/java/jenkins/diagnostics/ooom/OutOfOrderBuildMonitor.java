package jenkins.diagnostics.ooom;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reports any {@link Problem}s found and report them in the "Manage Jenkins" page.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class OutOfOrderBuildMonitor extends AdministrativeMonitor {
    private final Set<Problem> problems = new LinkedHashSet<Problem>();

    @Override
    public boolean isActivated() {
        return !problems.isEmpty();
    }

    void addProblem(Problem p) {
        problems.add(p);
    }
}
