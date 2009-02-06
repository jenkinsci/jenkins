/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
