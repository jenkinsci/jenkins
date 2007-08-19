package hudson.maven;

import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

/**
 * {@link ChangeLogSet} implementation used for {@link MavenBuild}.
 *
 * @author Kohsuke Kawaguchi
 */
public class FilteredChangeLogSet extends ChangeLogSet<Entry> {
    private final List<Entry> master = new ArrayList<Entry>();

    public final ChangeLogSet<? extends Entry> core;

    /*package*/ FilteredChangeLogSet(MavenBuild build) {
        super(build);
        MavenModule mod = build.getParent();

        // modules that are under 'mod'. lazily computed
        List<MavenModule> subsidiaries = null;

        MavenModuleSetBuild parentBuild = build.getParentBuild();
        if(parentBuild==null) {
            core = ChangeLogSet.createEmpty(build);
            return;
        }
        
        core = parentBuild.getChangeSet();

        for (Entry e : core) {
            boolean belongs = false;

            for (String path : e.getAffectedPaths()) {
                if(path.startsWith(mod.getRelativePath())) {
                    belongs = true;
                    break;
                }
            }

            if(belongs) {
                // make sure at least one change belongs to this module proper,
                // and not its subsidiary module
                if(subsidiaries==null) {
                    subsidiaries = new ArrayList<MavenModule>();
                    for (MavenModule mm : mod.getParent().getModules()) {
                        if(mm!=mod && mm.getRelativePath().startsWith(mod.getRelativePath()))
                            subsidiaries.add(mm);
                    }
                }

                belongs = false;

                for (String path : e.getAffectedPaths()) {
                    if(!belongsToSubsidiary(subsidiaries, path)) {
                        belongs = true;
                        break;
                    }
                }

                if(belongs)
                    master.add(e);
            }
        }
    }

    private boolean belongsToSubsidiary(List<MavenModule> subsidiaries, String path) {
        for (MavenModule sub : subsidiaries)
            if(path.startsWith(sub.getRelativePath()))
                return true;
        return false;
    }

    public Iterator<Entry> iterator() {
        return master.iterator();
    }

    public boolean isEmptySet() {
        return master.isEmpty();
    }

    public List<Entry> getLogs() {
        return master;
    }
}
