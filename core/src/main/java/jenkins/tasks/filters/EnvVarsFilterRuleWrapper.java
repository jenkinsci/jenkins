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
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class that provide the list of rules (local + global) for a given builder.
 *
 * @since 2.246
 */
@Restricted(NoExternalUse.class)
public class EnvVarsFilterRuleWrapper implements Serializable {
    private static final long serialVersionUID = -8647970104978388598L;
    private List<EnvVarsFilterRule> rules;

    public EnvVarsFilterRuleWrapper(@NonNull List<EnvVarsFilterRule> rules) {
        this.rules = rules;
    }

    public static @NonNull
    EnvVarsFilterRuleWrapper createRuleWrapper(@CheckForNull Run<?,?> run,
                                               @NonNull Object builder,
                                               @NonNull Launcher launcher,
                                               @NonNull List<EnvVarsFilterLocalRule> localRules) {
        List<EnvVarsFilterGlobalRule> globalRules = EnvVarsFilterGlobalConfiguration.getAllActivatedGlobalRules();
        List<EnvVarsFilterGlobalRule> applicableGlobalRules = globalRules.stream()
                .filter(rule -> rule.isApplicable(run, builder, launcher))
                .collect(Collectors.toList());

        List<EnvVarsFilterRule> applicableRules = new ArrayList<>();
        applicableRules.addAll(localRules);
        applicableRules.addAll(applicableGlobalRules);

        return new EnvVarsFilterRuleWrapper(applicableRules);
    }

    public void filter(@NonNull EnvVars envVars, @NonNull Launcher launcher, @NonNull TaskListener listener) throws EnvVarsFilterException {
        EnvVarsFilterRuleContext context = new EnvVarsFilterRuleContext(launcher, listener);
        for (EnvVarsFilterRule rule : rules) {
            try {
                rule.filter(envVars, context);
            } catch (EnvVarsFilterException e) {
                String message = String.format("Environment variable filtering failed due to violation with the message: %s", e.getMessage());
                context.getTaskListener().error(message);
                throw e;
            }
        }
    }
}
