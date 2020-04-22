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
import hudson.util.FormValidation;
import jenkins.tasks.filters.EnvVarsFilterRuleContext;
import jenkins.tasks.filters.EnvVarsFilterLocalRule;
import jenkins.tasks.filters.EnvVarsFilterLocalRuleDescriptor;
import jenkins.tasks.filters.EnvVarsFilterableBuilder;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local rule that removes all the non-retained variables for that step.
 *
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class RetainVariablesLocalRule implements EnvVarsFilterLocalRule {

    /**
     * List of lowercase names of variable that will be retained from removal
     */
    private String variables = "";
    private boolean retainCharacteristicEnvVars = true;
    private boolean retainProcessVariables = true;

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
            if (StringUtils.isNotBlank(nameFragment)) {
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

    public boolean isRetainCharacteristicEnvVars() {
        return retainCharacteristicEnvVars;
    }

    private List<String> variablesToRetain() {
        List<String> vars = new ArrayList<>(convertStringToList(this.variables));
        if (isRetainCharacteristicEnvVars()) {
            // TODO Make the 'HUDSON_COOKIE' variable less special so we can remove it.
            vars.addAll(Arrays.asList("jenkins_server_cookie", "hudson_server_cookie", "job_name", "job_base_name", "build_number", "build_id", "build_tag"));
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
                    if (retainProcessVariables) {
                        if (!systemValue.equals(variableValue)) {
                            variablesReset.add(variableName);
                        }
                    } else {
                        variablesRemoved.add(variableName);
                        iterator.remove();
                    }
                }
            }
        }

        if (!variablesRemoved.isEmpty()) {
            context.getTaskListener().getLogger().println(Messages.RetainVariablesLocalRule_RemovalMessage(getDescriptor().getDisplayName(), StringUtils.join(variablesRemoved.toArray(), ", ")));
        }
        if (!variablesReset.isEmpty()) {
            // reset the variables using the initial value from System
            variablesReset.forEach(variableName -> envVars.put(variableName, systemEnvVars.get(variableName)));
            context.getTaskListener().getLogger().println(Messages.RetainVariablesLocalRule_ResetMessage(getDescriptor().getDisplayName(), StringUtils.join(variablesReset.toArray(), ", ")));
        }
    }

    public boolean isRetainProcessVariables() {
        return retainProcessVariables;
    }

    @DataBoundSetter
    public void setRetainProcessVariables(boolean retainProcessVariables) {
        this.retainProcessVariables = retainProcessVariables;
    }

    // the ordinal is used to sort the rules in term of execution, the smaller value first
    // and take care of the fact that local rules are always applied before global ones
    @Extension(ordinal = DescriptorImpl.ORDER)
    @Symbol("retainOnlyVariables")
    public static final class DescriptorImpl extends EnvVarsFilterLocalRuleDescriptor {
        public static final int ORDER = 1000;

        public DescriptorImpl() {
            super();
            load();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckRetainCharacteristicEnvVars(@QueryParameter boolean value) {
            if (!value) {
                return FormValidation.warning(Messages.RetainVariablesLocalRule_CharacteristicEnvVarsFormValidationWarning());
            }
            return FormValidation.ok(Messages.RetainVariablesLocalRule_CharacteristicEnvVarsFormValidationOK());
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckRetainProcessVariables(@QueryParameter boolean value) {
            if (!value) {
                return FormValidation.warningWithMarkup(Messages.RetainVariablesLocalRule_ProcessVariablesFormValidationWarning());
            }
            return FormValidation.ok(Messages.RetainVariablesLocalRule_ProcessVariablesFormValidationOK());
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
}
