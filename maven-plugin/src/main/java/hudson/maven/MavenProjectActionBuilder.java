/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import hudson.model.Action;
import hudson.tasks.BuildStep;

import java.util.Collection;

/**
 * Can contribute to project actions.
 * 
 *
 * @author Kohsuke Kawaguchi
 * @see MavenBuildProxy#registerAsProjectAction(MavenProjectActionBuilder)
 */
public interface MavenProjectActionBuilder {
    /**
     * Equivalent of {@link BuildStep#getProjectActions(AbstractProject)}.
     *
     * <p>
     * Registers a transient action to {@link MavenModule} when it's rendered.
     * This is useful if you'd like to display an action at the module level.
     *
     * <p>
     * Since this contributes a transient action, the returned {@link Action}
     * will not be serialized.
     *
     * <p>
     * For this method to be invoked, call
     * {@link MavenBuildProxy#registerAsProjectAction(MavenProjectActionBuilder)} during the build.
     *
     * @return
     *      can be empty but never null.
     * @since 1.341
     */
    Collection<? extends Action> getProjectActions(MavenModule module);
}
