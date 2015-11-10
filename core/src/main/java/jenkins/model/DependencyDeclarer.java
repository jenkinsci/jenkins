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
package jenkins.model;

import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;

/**
 * Marker interface for project-associated objects that can participate
 * in the dependency graph computation process.
 *
 * <p>
 * {@link Publisher}s, {@link Builder}s, {@link BuildWrapper}s, and {@link Trigger}s
 * (or whatever {@link DescribableList#buildDependencyGraph} is called on, by {@link AbstractProject#buildDependencyGraph})
 * can implement this interface to add additional edges to the dependency graph.
 *
 * @author Nicolas Lalevee
 * @author Martin Ficker
 * @author Kohsuke Kawaguchi
 * @since 1.501
 */
public interface DependencyDeclarer {
    // I thought about whether this should extend BuildStep or not and decided not to.
    // so that this concept can be extended elsewhere, like maven projects and so on.

    /**
     * Invoked from {@link AbstractProject#buildDependencyGraph(DependencyGraph)}.
     *
     * @param owner
     *      The project that owns the publishers, builders, etc.
     *      This information is conceptually redundant, as those objects are
     *      only configured against the single owner, but this information is
     *      nevertheless passed in since often owner information is not recorded.
     *      Never null.
     * @param graph
     *      The dependency graph being built. Never null.
     */
    void buildDependencyGraph(AbstractProject owner, DependencyGraph graph);
}
