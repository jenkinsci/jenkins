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
package hudson.tasks;

import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Action;
import hudson.model.Project;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.Launcher;

import java.io.IOException;

/**
 * Provides compatibility with {@link BuildStep} before 1.150
 * so that old plugin binaries can continue to function with new Hudson.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.150
 */
public abstract class BuildStepCompatibilityLayer implements BuildStep {
//
// new definitions >= 1.150
//
    public boolean prebuild(AbstractBuild<?,?> build, BuildListener listener) {
        if (build instanceof Build)
            return prebuild((Build)build,listener);
        else
            return true;
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (build instanceof Build)
            return perform((Build)build,launcher,listener);
        else
            return true;
    }

    public Action getProjectAction(AbstractProject<?, ?> project) {
        if (project instanceof Project)
            return getProjectAction((Project) project);
        else
            return null;
    }
//
// old definitions < 1.150
//
    /**
     * @deprecated
     *      Use {@link #prebuild(AbstractBuild, BuildListener)} instead.
     */
    public boolean prebuild(Build<?,?> build, BuildListener listener) {
        return true;
    }

    /**
     * @deprecated
     *      Use {@link #perform(AbstractBuild, Launcher, BuildListener)} instead.
     */
    public boolean perform(Build<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     *      Use {@link #getProjectAction(AbstractProject)} instead.
     */
    public Action getProjectAction(Project<?,?> project) {
        return null;
    }
}
