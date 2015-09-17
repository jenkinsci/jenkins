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

import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import hudson.model.AbstractProject.AbstractProjectDescriptor;
import hudson.Extension;
import hudson.util.DescriptorList;

import java.util.ArrayList;
import java.util.List;

/**
 * List of all installed {@link BuildWrapper}.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildWrappers {
    /**
     * @deprecated
     *      as of 1.281. Use {@link Extension} for registration, and use {@link BuildWrapper#all()}
     *      for listing them.
     */
    @Deprecated
    public static final List<Descriptor<BuildWrapper>> WRAPPERS = new DescriptorList<>(BuildWrapper.class);

    /**
     * List up all {@link BuildWrapperDescriptor}s that are applicable for the given project.
     *
     * @return
     *      The signature doesn't use {@link BuildWrapperDescriptor} to maintain compatibility
     *      with {@link BuildWrapper} implementations before 1.150.
     */
    public static List<Descriptor<BuildWrapper>> getFor(AbstractProject<?, ?> project) {
        List<Descriptor<BuildWrapper>> result = new ArrayList<>();
        Descriptor pd = Jenkins.getInstance().getDescriptor((Class)project.getClass());

        for (Descriptor<BuildWrapper> w : BuildWrapper.all()) {
            if (pd instanceof AbstractProjectDescriptor && !((AbstractProjectDescriptor)pd).isApplicable(w))
                continue;
            if (w instanceof BuildWrapperDescriptor) {
                BuildWrapperDescriptor bwd = (BuildWrapperDescriptor) w;
                if(bwd.isApplicable(project))
                    result.add(bwd);
            } else {
                // old BuildWrapper that doesn't implement BuildWrapperDescriptor
                result.add(w);
            }
        }
        return result;
    }
}
