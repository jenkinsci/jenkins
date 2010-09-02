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
import java.util.Collections;

/**
 * {@link ChangeLogSet} implementation used for {@link MavenBuild}.
 *
 * @author Kohsuke Kawaguchi
 */
public class FilteredChangeLogSet extends ChangeLogSet<Entry> {
    private final List<Entry> master;

    public final ChangeLogSet<? extends Entry> core;

    /*package*/ FilteredChangeLogSet(MavenBuild build) {
        super(build);
        MavenModuleSetBuild parentBuild = build.getParentBuild();
        if(parentBuild==null) {
            core = ChangeLogSet.createEmpty(build);
            master = Collections.emptyList();
        } else {
            core = parentBuild.getChangeSet();
            master = parentBuild.getChangeSetFor(build.getParent());
        }
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
