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

import hudson.ExtensionPoint;
import hudson.ExtensionList;
import hudson.Extension;
import jenkins.model.Jenkins;
import org.apache.maven.project.MavenProject;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Extension point in Hudson to find additional dependencies from {@link MavenProject}.
 *
 * <p>
 * Maven plugin configurations often have additional configuration entries to specify
 * artifacts that a build depends on. Plugins can contribute an implementation of
 * this interface to find such hidden dependencies.
 *
 * <p>
 * To register implementations, put {@link Extension} on your subclass.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.264
 * @see HUDSON-2685
 */
public abstract class ModuleDependencyLocator implements ExtensionPoint {
    /**
     * Discovers hidden dependencies.
     *
     * @param project
     *      In memory representation of Maven project, from which the hidden dependencies will be extracted.
     *      Never null.
     * @param pomInfo
     *      Partially filled {@link PomInfo} object. Dependencies returned from this method will be
     *      added to this object by the caller.
     */
    public abstract Collection<ModuleDependency> find(MavenProject project, PomInfo pomInfo);

    /**
     * Returns all the registered {@link ModuleDependencyLocator} descriptors.
     */
    public static ExtensionList<ModuleDependencyLocator> all() {
        return Jenkins.getInstance().getExtensionList(ModuleDependencyLocator.class);
    }

    /**
     * Facade of {@link ModuleDependencyLocator}.
     */
    /*package*/ static class ModuleDependencyLocatorFacade extends ModuleDependencyLocator {
        @Override
        public Collection<ModuleDependency> find(MavenProject project, PomInfo pomInfo) {
            Set<ModuleDependency> r = new HashSet<ModuleDependency>();
            for (ModuleDependencyLocator m : all())
                r.addAll(m.find(project,pomInfo));
            return r;
        }
    }
}
