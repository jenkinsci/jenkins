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

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.ItemGroup;
import hudson.triggers.Trigger;
import hudson.tasks.Maven.ProjectWithMaven;

import java.util.HashSet;
import java.util.Set;

/**
 * Common part between {@link MavenModule} and {@link MavenModuleSet}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractMavenProject<P extends AbstractMavenProject<P,R>,R extends AbstractBuild<P,R>> extends AbstractProject<P,R>
    implements ProjectWithMaven {
    protected AbstractMavenProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    protected void updateTransientActions() {
        synchronized(transientActions) {
            super.updateTransientActions();

            // if we just pick up the project actions from the last build,
            // and if the last build failed very early, then the reports that
            // kick in later (like test results) won't be displayed.
            // so pick up last successful build, too.
            Set<Class> added = new HashSet<Class>();
            addTransientActionsFromBuild(getLastBuild(),added);
            addTransientActionsFromBuild(getLastSuccessfulBuild(),added);

            for (Trigger trigger : triggers) {
                Action a = trigger.getProjectAction();
                if(a!=null)
                    transientActions.add(a);
            }
        }
    }

    protected abstract void addTransientActionsFromBuild(R lastBuild, Set<Class> added);
}
