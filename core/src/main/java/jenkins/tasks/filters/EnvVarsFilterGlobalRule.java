/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
package jenkins.tasks.filters;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import java.io.Serializable;

/**
 * Environment variables filter rule that is configured globally for all jobs. <p>
 * The job types can be filtered using {@link #isApplicable(Run, Object, Launcher)}
 *
 * The local rules are applied before the global ones.
 *
 * @since 2.246
 */
@Restricted(Beta.class)
public interface EnvVarsFilterGlobalRule extends Describable<EnvVarsFilterGlobalRule>, EnvVarsFilterRule, ExtensionPoint, Serializable {
    @SuppressWarnings("unchecked")
    default Descriptor<EnvVarsFilterGlobalRule> getDescriptor() {
        return (Descriptor<EnvVarsFilterGlobalRule>) Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * @param run The executing run that has one of its step requiring environment filters
     * @param builder Normally inherits from {@link EnvVarsFilterableBuilder} but not forced to let reflection usage in plugins
     * @param launcher The launcher that will be used to run the command
     * @return true iff the rule can be applied to that builder
     */
    boolean isApplicable(@CheckForNull Run<?,?> run, @NonNull Object builder, @NonNull Launcher launcher);
}
