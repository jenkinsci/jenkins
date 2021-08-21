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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Descriptor for the local rule. Compared to the global rule, it's the descriptor that determine
 * if the rule is applicable to a given builder and then applied every time. <p>
 * For global rule it's the inverse, the rule itself determines when it's applicable.
 *
 * @since 2.246
 */
@Restricted(Beta.class)
public abstract class EnvVarsFilterLocalRuleDescriptor extends Descriptor<EnvVarsFilterLocalRule> {
    public abstract boolean isApplicable(@NonNull Class<? extends EnvVarsFilterableBuilder> builderClass);

    public static List<EnvVarsFilterLocalRuleDescriptor> allApplicableFor(Class<? extends EnvVarsFilterableBuilder> builderClass) {
        DescriptorExtensionList<EnvVarsFilterLocalRule, EnvVarsFilterLocalRuleDescriptor> allSpecificRules =
                Jenkins.get().getDescriptorList(EnvVarsFilterLocalRule.class);

        return allSpecificRules.stream()
                .filter(rule -> rule.isApplicable(builderClass))
                .collect(Collectors.toList());
    }
}
