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

import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import java.io.Serializable;

/**
 * Environment variables filter rule that is specific to a job configuration, using script-specific variables, etc.<p>
 * The job types can be filtered using {@link EnvVarsFilterLocalRuleDescriptor#isApplicable(Class)}
 *
 * The local rules are applied before the global ones.
 *
 * @since 2.246
 */
@Restricted(Beta.class)
public interface EnvVarsFilterLocalRule extends Describable<EnvVarsFilterLocalRule>, EnvVarsFilterRule, ExtensionPoint, Serializable {
    default EnvVarsFilterLocalRuleDescriptor getDescriptor() {
        return (EnvVarsFilterLocalRuleDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }
}
