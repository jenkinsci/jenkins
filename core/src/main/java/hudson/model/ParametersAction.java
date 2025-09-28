/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jean-Baptiste Quenot, Seiji Sogabe, Tom Huybrechts
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

package hudson.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Queue.QueueAction;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.SubTask;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.util.VariableResolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.RunAction2;
import jenkins.model.experimentalflags.NewBuildPageUserExperimentalFlag;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Records the parameter values used for a build.
 *
 * <P>
 * This object is associated with the build record so that we remember what parameters
 * were used for what build. It is also attached to the queue item to remember parameter
 * that were specified when scheduling.
 */
@ExportedBean
public class ParametersAction implements RunAction2, Iterable<ParameterValue>, QueueAction, EnvironmentContributingAction, LabelAssignmentAction {

    /**
     * Three state variable (null, false, true).
     *
     * If explicitly set to true, it will keep all variable, explicitly set to
     * false it will drop all of them (except if they are marked safe).
     * If null, and they are not safe, it will log a warning in logs to the user
     * to let him choose the behavior
     *
     * @since 2.3
     */
    @Restricted(NoExternalUse.class)
    public static final String KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME = ParametersAction.class.getName() +
            ".keepUndefinedParameters";

    @Restricted(NoExternalUse.class)
    public static final String SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME = ParametersAction.class.getName() +
            ".safeParameters";

    private Set<String> safeParameters;

    private @NonNull List<ParameterValue> parameters;

    private List<String> parameterDefinitionNames;

    /**
     * @deprecated since 1.283; kept to avoid warnings loading old build data, but now transient.
     */
    @Deprecated
    private transient AbstractBuild<?, ?> build;

    private transient Run<?, ?> run;

    public ParametersAction(@NonNull List<ParameterValue> parameters) {
        this.parameters = new ArrayList<>(parameters);
        String paramNames = SystemProperties.getString(SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME);
        safeParameters = new TreeSet<>();
        if (paramNames != null) {
            safeParameters.addAll(Arrays.asList(paramNames.split(",")));
        }
    }

    /**
     * Constructs a new action with additional safe parameters.
     * The additional safe parameters should be only those considered safe to override the environment
     * and what is declared in the project config in addition to those specified by the user in
     * {@link #SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME}.
     * See <a href="https://issues.jenkins.io/browse/SECURITY-170">SECURITY-170</a>
     *
     * @param parameters the parameters
     * @param additionalSafeParameters additional safe parameters
     * @since 1.651.2, 2.3
     */
    public ParametersAction(List<ParameterValue> parameters, Collection<String> additionalSafeParameters) {
        this(parameters);
        if (additionalSafeParameters != null) {
            safeParameters.addAll(additionalSafeParameters);
        }
    }

    public ParametersAction(ParameterValue... parameters) {
        this(Arrays.asList(parameters));
    }

    public void createBuildWrappers(AbstractBuild<?, ?> build, Collection<? super BuildWrapper> result) {
        for (ParameterValue p : getParameters()) {
            if (p == null) continue;
            BuildWrapper w = p.createBuildWrapper(build);
            if (w != null) result.add(w);
        }
    }

    @Override
    public void buildEnvironment(Run<?, ?> run, EnvVars env) {
        for (ParameterValue p : getParameters()) {
            if (p == null) continue;
            p.buildEnvironment(run, env);
        }
    }

    // TODO do we need an EnvironmentContributingAction variant that takes Run so this can implement it?

    /**
     * Performs a variable substitution to the given text and return it.
     */
    public String substitute(AbstractBuild<?, ?> build, String text) {
        return Util.replaceMacro(text, createVariableResolver(build));
    }

    /**
     * Creates an {@link VariableResolver} that aggregates all the parameters.
     *
     * <p>
     * If you are a {@link BuildStep}, most likely you should call {@link AbstractBuild#getBuildVariableResolver()}.
     */
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        VariableResolver[] resolvers = new VariableResolver[getParameters().size() + 1];
        int i = 0;
        for (ParameterValue p : getParameters()) {
            if (p == null) continue;
            resolvers[i++] = p.createVariableResolver(build);
        }

        resolvers[i] = build.getBuildVariableResolver();

        return new VariableResolver.Union<String>(resolvers);
    }

    @Override
    public Iterator<ParameterValue> iterator() {
        return getParameters().iterator();
    }

    @Exported(visibility = 2)
    public List<ParameterValue> getParameters() {
        return Collections.unmodifiableList(filter(parameters));
    }

    public ParameterValue getParameter(String name) {
        for (ParameterValue p : parameters) {
            if (p == null) continue;
            if (p.getName().equals(name))
                return p;
        }
        return null;
    }

    @Override
    public Label getAssignedLabel(SubTask task) {
        for (ParameterValue p : getParameters()) {
            if (p == null) continue;
            Label l = p.getAssignedLabel(task);
            if (l != null)    return l;
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.ParameterAction_DisplayName();
    }

    @Override
    public String getIconFileName() {
        Boolean newBuildPageEnabled = new NewBuildPageUserExperimentalFlag().getFlagValue();

        if (newBuildPageEnabled) {
            return null;
        }

        return "symbol-parameters";
    }

    @Override
    public String getUrlName() {
        return "parameters";
    }

    /**
     * Allow an other build of the same project to be scheduled, if it has other parameters.
     */
    @Override
    public boolean shouldSchedule(List<Action> actions) {
        List<ParametersAction> others = Util.filter(actions, ParametersAction.class);
        if (others.isEmpty()) {
            return !parameters.isEmpty();
        } else {
            // I don't think we need multiple ParametersActions, but let's be defensive
            Set<ParameterValue> params = new HashSet<>();
            for (ParametersAction other : others) {
                params.addAll(other.parameters);
            }
            return !params.equals(new HashSet<>(this.parameters));
        }
    }

    /**
     * Creates a new {@link ParametersAction} that contains all the parameters in this action
     * with the overrides / new values given as parameters.
     * @return New {@link ParametersAction}. The result may contain null {@link ParameterValue}s
     */
    @NonNull
    public ParametersAction createUpdated(Collection<? extends ParameterValue> overrides) {
        if (overrides == null) {
            ParametersAction parametersAction = new ParametersAction(parameters);
            parametersAction.safeParameters = this.safeParameters;
            return parametersAction;
        }
        List<ParameterValue> combinedParameters = new ArrayList<>(overrides);
        Set<String> names = new HashSet<>();

        for (ParameterValue v : overrides) {
            if (v == null) continue;
            names.add(v.getName());
        }

        for (ParameterValue v : parameters) {
            if (v == null) continue;
            if (!names.contains(v.getName())) {
                combinedParameters.add(v);
            }
        }

        return new ParametersAction(combinedParameters, this.safeParameters);
    }

    /*
     * Creates a new {@link ParametersAction} that contains all the parameters in this action
     * with the overrides / new values given as another {@link ParametersAction}.
     * @return New {@link ParametersAction}. The result may contain null {@link ParameterValue}s
     */
    @NonNull
    public ParametersAction merge(@CheckForNull ParametersAction overrides) {
        if (overrides == null) {
            return new ParametersAction(parameters, this.safeParameters);
        }
        ParametersAction parametersAction = createUpdated(overrides.parameters);
        Set<String> safe = new TreeSet<>();
        if (parametersAction.safeParameters != null && this.safeParameters != null) {
            safe.addAll(this.safeParameters);
        }
        if (overrides.safeParameters != null) {
            safe.addAll(overrides.safeParameters);
        }
        parametersAction.safeParameters = safe;
        return parametersAction;
    }

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "parameters in readResolve is needed for data migration.")
    private Object readResolve() {
        if (parameters == null) { // JENKINS-39495
            parameters = Collections.emptyList();
        }
        if (build != null)
            OldDataMonitor.report(build, "1.283");
        if (safeParameters == null) {
            safeParameters = Collections.emptySet();
        }
        return this;
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        ParametersDefinitionProperty p = r.getParent().getProperty(ParametersDefinitionProperty.class);
        if (p != null) {
            this.parameterDefinitionNames = new ArrayList<>(p.getParameterDefinitionNames());
        } else {
            this.parameterDefinitionNames = Collections.emptyList();
        }
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        this.run = r;
    }

    private List<? extends ParameterValue> filter(List<ParameterValue> parameters) {
        if (this.run == null) {
            return parameters;
        }

        if (this.parameterDefinitionNames == null) {
            return parameters;
        }

        Boolean shouldKeepFlag = SystemProperties.optBoolean(KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME);
        if (shouldKeepFlag != null && shouldKeepFlag) {
            return parameters;
        }

        List<ParameterValue> filteredParameters = new ArrayList<>();

        for (ParameterValue v : this.parameters) {
            if (this.parameterDefinitionNames.contains(v.getName()) || isSafeParameter(v.getName())) {
                filteredParameters.add(v);
            } else if (shouldKeepFlag == null) {
                LOGGER.log(Level.WARNING, "Skipped parameter `{0}` as it is undefined on `{1}` (#{2}). Set `-D{3}=true` to allow "
                        + "undefined parameters to be injected as environment variables or `-D{4}=[comma-separated list]` to whitelist specific parameter names, "
                        + "even though it represents a security breach or `-D{3}=false` to no longer show this message.",
                        new Object [] { v.getName(), run.getParent().getFullName(), run.getNumber(),
                                KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME, SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME });
            }
        }

        return filteredParameters;
    }

    /**
     * Returns all parameters.
     *
     * Be careful in how you process them. It will return parameters even not being defined as
     * {@link ParametersDefinitionProperty} in the job, so any external
     * caller could inject any parameter (using any key) here. <strong>Treat it as untrusted data</strong>.
     *
     * @return all parameters defined here.
     * @since 1.651.2, 2.3
     */
    public List<ParameterValue> getAllParameters() {
        return Collections.unmodifiableList(parameters);
    }

    private boolean isSafeParameter(String name) {
        return safeParameters.contains(name);
    }

    private static final Logger LOGGER = Logger.getLogger(ParametersAction.class.getName());

}
