package jenkins.bootstrap;

import java.util.ArrayList;
import java.util.List;

/**
 * Records what core component got overridden by what.
 *
 * <p>
 * This record is used to assist diagnostics.
 * 
 * @author Kohsuke Kawaguchi
 * @see Bootstrap#getOverrides()
 */
public final class OverrideJournal {
    /**
     * From a component that got overridden to a component that overrode it.
     */
    private final List<OverrideEntry> data = new ArrayList<>();

    /*package*/ OverrideJournal() {}

    /**
     * Records the fact that these dependencies overrode their corresponding core components.
     */
    /*package*/ void add(DependenciesTxt core, Plugin p, DependenciesTxt deps) {
        for (Dependency dep : deps.dependencies) {
            Dependency base = core.getMatchingDependency(dep);
            assert base!=null;
            data.add(new OverrideEntry(base,dep,p));
        }
    }

    /**
     * Is the given dependency in the core overriden?
     */
    boolean isOverridden(Dependency d) {
        for (OverrideEntry o : data) {
            if (o.getFrom().equals(d))
                return true;
        }
        return false;
    }

    /**
     * If a component of the given groupId + artifactId are overridden, return the component that overrode it.
     */
    public OverrideEntry getOverrideOf(String groupId, String artifactId) {
        String ga = groupId + ':' + artifactId;
        for (OverrideEntry o : data) {
            if (o.getFrom().ga.equals(ga))
                return o;
        }
        return null;
    }
}
