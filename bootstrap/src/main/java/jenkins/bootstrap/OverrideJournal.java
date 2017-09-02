package jenkins.bootstrap;

import java.util.HashMap;
import java.util.Map;

/**
 * Records what component got overridden by what.
 *
 * <p>
 * This record is used to assist diagnostics.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class OverrideJournal {
    private final Map<Dependency,Dependency> data = new HashMap<>();

    /*package*/ OverrideJournal() {}

    /**
     * Records the fact that these dependencies overrode their corresponding core components.
     */
    /*package*/ void add(DependenciesTxt core, DependenciesTxt deps) {
        for (Dependency dep : deps.dependencies) {
            Dependency base = core.getMatchingDependency(dep);
            assert base!=null;
            data.put(base,dep);
        }
    }

    /**
     * Is the given dependency in the core overriden?
     */
    boolean isOverridden(Dependency d) {
        return data.containsKey(d);
    }
}
