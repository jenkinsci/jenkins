/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Koichi Fujikawa, Red Hat, Inc., Seiji Sogabe, Stephen Connolly, Tom Huybrechts, Yahoo! Inc, Robert Collins
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

package hudson.model;

import hudson.tasks.LabelFinder;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

/**
 * Dynamic labels (with its recomputing logic.)
 *
 * @author Robert Collins <robertc@robertcollins.net>
 */
public final class DynamicLabels {
    /**
     * Stores the computer object for which dynamic labels have been
     * computed, so that we can detect when we need to recompute.
     */
    private final Computer computer;
    /**
     * Stores the last set of dynamically calculated labels. Read-only.
     */
    public final Set<Label> labels;

    /**
     * Create dynamic labels for a computer.
     */
    public DynamicLabels(Computer comp) {
        labels = buildLabels(comp);
        computer = comp;
    }

    /**
     * Determine if the labels cached in this DynamicLabels is up to date.
     */
    public boolean isChanged(Computer comp) {
        return comp!=computer;
    }

    /**
     * Read labels from channel into this.labels.
     */
    private static Set<Label> buildLabels(Computer c) {
        if (null == c)
            return Collections.emptySet();

        Set<Label> r = new HashSet<Label>();
        for (LabelFinder labeler : LabelFinder.all())
            for (String label : labeler.findLabels(c.getNode()))
                r.add(Hudson.getInstance().getLabel(label));
        return r;
    }
}
