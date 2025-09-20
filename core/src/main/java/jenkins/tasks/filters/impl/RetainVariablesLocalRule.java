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

package jenkins.tasks.filters.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jenkins.tasks.filters.EnvVarsFilterLocalRule;
import jenkins.tasks.filters.EnvVarsFilterLocalRuleDescriptor;
import jenkins.tasks.filters.EnvVarsFilterRuleContext;
import jenkins.tasks.filters.EnvVarsFilterableBuilder;
import org.jenkinsci.Symbol;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Local rule that removes all the non-retained variables for that step.
 *
 * @since 2.246
 */
@Restricted(NoExternalUse.class)
public class RetainVariablesLocalRule implements EnvVarsFilterLocalRule {

    /**
     * The variables considered to be 'characteristic' for the purposes of this rule.
     *
     * @see Job#getCharacteristicEnvVars()
     * @see Run#getCharacteristicEnvVars()
     */
    // TODO Make the 'HUDSON_COOKIE' variable less special so we can remove it.
    // TODO consider just querying the build, if any, for its characteristic env vars
    private static final List<String> CHARACTERISTIC_ENV_VARS = Arrays.asList("jenkins_server_cookie", "hudson_server_cookie", "job_name", "job_base_name", "build_number", "build_id", "build_tag");
    /**
     * List of lowercase names of variable that will be retained from removal
     */
    private String variables = "";
    private boolean retainCharacteristicEnvVars = true;
    private ProcessVariablesHandling processVariablesHandling = ProcessVariablesHandling.RESET;

    @DataBoundConstructor
    public RetainVariablesLocalRule() {
    }

    @DataBoundSetter
    public void setVariables(@NonNull String variables) {
        this.variables = variables;
    }

    private static List<String> convertStringToList(@NonNull String variablesCommaSeparated) {
        String[] variablesArray = variablesCommaSeparated.split("\\s+");
        List<String> variables = new ArrayList<>();
        for (String nameFragment : variablesArray) {
            if (nameFragment != null && !nameFragment.isBlank()) {
                variables.add(nameFragment.toLowerCase(Locale.ENGLISH));
            }
        }

        Collections.sort(variables); // TODO do we really want to sort this?
        return variables;
    }

    // for jelly view
    @Restricted(NoExternalUse.class)
    public @NonNull String getVariables() {
        return variables;
    }

    @DataBoundSetter
    public void setRetainCharacteristicEnvVars(boolean retainCharacteristicEnvVars) {
        this.retainCharacteristicEnvVars = retainCharacteristicEnvVars;
    }

    /**
     * Whether to retain characteristic environment variables.
     * @return true if and only if to retain characteristic environment variables.
     *
     * @see Job#getCharacteristicEnvVars()
     * @see Run#getCharacteristicEnvVars()
     */
    public boolean isRetainCharacteristicEnvVars() {
        return retainCharacteristicEnvVars;
    }

    private List<String> variablesToRetain() {
        List<String> vars = new ArrayList<>(convertStringToList(this.variables));
        if (isRetainCharacteristicEnvVars()) {
            vars.addAll(CHARACTERISTIC_ENV_VARS);
        }
        return vars;
    }

    @Override
    public void filter(@NonNull EnvVars envVars, @NonNull EnvVarsFilterRuleContext context) {
        Map<String, String> systemEnvVars = EnvVars.masterEnvVars;

        final List<String> variablesRemoved = new ArrayList<>();
        final List<String> variablesReset = new ArrayList<>();
        final List<String> variables = variablesToRetain();
        for (Iterator<Map.Entry<String, String>> iterator = envVars.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, String> entry = iterator.next();
            String variableName = entry.getKey();
            String variableValue = entry.getValue();

            if (!variables.contains(variableName.toLowerCase(Locale.ENGLISH))) {
                // systemEnvVars's keys are case insensitive
                String systemValue = systemEnvVars.get(variableName);

                if (systemValue == null) {
                    variablesRemoved.add(variableName);
                    iterator.remove();
                } else {
                    switch (processVariablesHandling) {
                        case RESET:
                            if (!systemValue.equals(variableValue)) {
                                variablesReset.add(variableName);
                            }
                            break;
                        case REMOVE:
                            variablesRemoved.add(variableName);
                            iterator.remove();
                            break;
                        default:
                            throw new AssertionError("Unknown process variables handling: " + processVariablesHandling);
                    }
                }
            }
        }

        if (!variablesRemoved.isEmpty()) {
            context.getTaskListener().getLogger().println(Messages.RetainVariablesLocalRule_RemovalMessage(getDescriptor().getDisplayName(), String.join(", ", variablesRemoved)));
        }
        if (!variablesReset.isEmpty()) {
            // reset the variables using the initial value from System
            variablesReset.forEach(variableName -> envVars.put(variableName, systemEnvVars.get(variableName)));
            context.getTaskListener().getLogger().println(Messages.RetainVariablesLocalRule_ResetMessage(getDescriptor().getDisplayName(), String.join(", ", variablesReset)));
        }
    }

    public ProcessVariablesHandling getProcessVariablesHandling() {
        return processVariablesHandling;
    }

    @DataBoundSetter
    public void setProcessVariablesHandling(ProcessVariablesHandling processVariablesHandling) {
        this.processVariablesHandling = processVariablesHandling;
    }

    // the ordinal is used to sort the rules in term of execution, the higher value first
    // and take care of the fact that local rules are always applied before global ones
    @Extension(ordinal = 1000)
    @Symbol("retainOnlyVariables")
    public static final class DescriptorImpl extends EnvVarsFilterLocalRuleDescriptor {

        public DescriptorImpl() {
            load();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckRetainCharacteristicEnvVars(@QueryParameter boolean value) {
            if (!value) {
                return FormValidation.warning(Messages.RetainVariablesLocalRule_CharacteristicEnvVarsFormValidationWarning());
            }
            return FormValidation.ok(Messages.RetainVariablesLocalRule_CharacteristicEnvVarsFormValidationOK());
        }

        @Override
        public @NonNull String getDisplayName() {
            return Messages.RetainVariablesLocalRule_DisplayName();
        }

        @Override
        public boolean isApplicable(@NonNull Class<? extends EnvVarsFilterableBuilder> builderClass) {
            return true;
        }
    }

    public enum ProcessVariablesHandling {
        RESET(Messages._RetainVariablesLocalRule_RESET_DisplayName()),
        REMOVE(Messages._RetainVariablesLocalRule_REMOVE_DisplayName());

        private final Localizable localizable;

        ProcessVariablesHandling(Localizable localizable) {
            this.localizable = localizable;
        }

        public String getDisplayName() {
            return localizable.toString();
        }
    }
}
