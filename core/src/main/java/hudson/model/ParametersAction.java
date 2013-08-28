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

import hudson.Util;
import hudson.EnvVars;
import hudson.diagnosis.OldDataMonitor;
import hudson.matrix.MatrixChildAction;
import hudson.model.Queue.QueueAction;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.SubTask;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.util.VariableResolver;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Records the parameter values used for a build.
 *
 * <P>
 * This object is associated with the build record so that we remember what parameters
 * were used for what build. It is also attached to the queue item to remember parameter
 * that were specified when scheduling.
 */
@ExportedBean
public class ParametersAction implements Action, Iterable<ParameterValue>, QueueAction, EnvironmentContributingAction, LabelAssignmentAction, MatrixChildAction {

    private final List<ParameterValue> parameters;

    /**
     * @deprecated since 1.283; kept to avoid warnings loading old build data, but now transient.
     */
    private transient AbstractBuild<?, ?> build;

    public ParametersAction(List<ParameterValue> parameters) {
        this.parameters = parameters;
    }
    
    public ParametersAction(ParameterValue... parameters) {
        this(Arrays.asList(parameters));
    }

    public void createBuildWrappers(AbstractBuild<?,?> build, Collection<? super BuildWrapper> result) {
        for (ParameterValue p : parameters) {
            BuildWrapper w = p.createBuildWrapper(build);
            if(w!=null) result.add(w);
        }
    }

    public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
        for (ParameterValue p : parameters)
            p.buildEnvVars(build,env);
    }

    /**
     * Performs a variable substitution to the given text and return it.
     */
    public String substitute(AbstractBuild<?,?> build, String text) {
        return Util.replaceMacro(text,createVariableResolver(build));
    }

    /**
     * Creates an {@link VariableResolver} that aggregates all the parameters.
     *
     * <p>
     * If you are a {@link BuildStep}, most likely you should call {@link AbstractBuild#getBuildVariableResolver()}. 
     */
    public VariableResolver<String> createVariableResolver(AbstractBuild<?,?> build) {
        VariableResolver[] resolvers = new VariableResolver[parameters.size()+1];
        int i=0;
        for (ParameterValue p : parameters)
            resolvers[i++] = p.createVariableResolver(build);

        resolvers[i] = build.getBuildVariableResolver();

        return new VariableResolver.Union<String>(resolvers);
    }

    public Iterator<ParameterValue> iterator() {
        return parameters.iterator();
    }

    @Exported(visibility=2)
    public List<ParameterValue> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public ParameterValue getParameter(String name) {
        for (ParameterValue p : parameters)
            if (p.getName().equals(name))
                return p;
        return null;
    }

    public Label getAssignedLabel(SubTask task) {
        for (ParameterValue p : parameters) {
            Label l = p.getAssignedLabel(task);
            if (l!=null)    return l;
        }
        return null;
    }

    public String getDisplayName() {
        return Messages.ParameterAction_DisplayName();
    }

    public String getIconFileName() {
        return "document-properties.png";
    }

    public String getUrlName() {
        return "parameters";
    }

    /**
     * Allow an other build of the same project to be scheduled, if it has other parameters.
     */
    public boolean shouldSchedule(List<Action> actions) {
        List<ParametersAction> others = Util.filter(actions, ParametersAction.class);
        if (others.isEmpty()) {
            return !parameters.isEmpty();
        } else {
            // I don't think we need multiple ParametersActions, but let's be defensive
            Set<ParameterValue> params = new HashSet<ParameterValue>();
            for (ParametersAction other: others) {
                params.addAll(other.parameters);
            }
            return !params.equals(new HashSet<ParameterValue>(this.parameters));
        }
    }

    /**
     * Creates a new {@link ParametersAction} that contains all the parameters in this action
     * with the overrides / new values given as parameters.
     */
    public ParametersAction createUpdated(Collection<? extends ParameterValue> newValues) {
        List<ParameterValue> r = new ArrayList<ParameterValue>();

        Set<String> names = new HashSet<String>();
        for (ParameterValue v : newValues) {
            names.add(v.name);
        }

        for (Iterator<ParameterValue> itr = parameters.iterator(); itr.hasNext(); ) {
            ParameterValue v = itr.next();
            if (!names.contains(v.getName()))
                r.add(v);
        }

        r.addAll(newValues);

        return new ParametersAction(r);
    }

    private Object readResolve() {
        if (build != null)
            OldDataMonitor.report(build, "1.283");
        return this;
    }
}
