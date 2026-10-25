/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Jean-Baptiste Quenot, Seiji Sogabe, Tom Huybrechts
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

import static jakarta.servlet.http.HttpServletResponse.SC_CREATED;
import static jakarta.servlet.http.HttpServletResponse.SC_SEE_OTHER;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Queue.WaitingItem;
import hudson.model.queue.ScheduleResult;
import hudson.util.AlternativeUiTextProvider;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.model.OptionalJobProperty;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.util.TimeDuration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Keeps a list of the parameters defined for a project.
 *
 * <p>
 * This class also implements {@link Action} so that {@code index.jelly} provides
 * a form to enter build parameters.
 * <p>The owning job needs a {@code sidepanel.jelly} and should have web methods delegating to {@link ParameterizedJobMixIn#doBuild} and {@link ParameterizedJobMixIn#doBuildWithParameters}.
 * The builds also need a {@code sidepanel.jelly}.
 */
@ExportedBean(defaultVisibility = 2)
public class ParametersDefinitionProperty extends OptionalJobProperty<Job<?, ?>>
        implements Action {

    public static final AlternativeUiTextProvider.Message<Job> BUILD_BUTTON_TEXT = new AlternativeUiTextProvider.Message<>();

    private final List<ParameterDefinition> parameterDefinitions;

    @DataBoundConstructor
    public ParametersDefinitionProperty(@NonNull List<ParameterDefinition> parameterDefinitions) {
        this.parameterDefinitions = parameterDefinitions != null ? parameterDefinitions : new ArrayList<>();
    }

    public ParametersDefinitionProperty(@NonNull ParameterDefinition... parameterDefinitions) {
        this.parameterDefinitions = parameterDefinitions != null ? Arrays.asList(parameterDefinitions) : new ArrayList<>();
    }

    private Object readResolve() {
        return parameterDefinitions == null ? new ParametersDefinitionProperty() : this;
    }


    public final String getBuildButtonText() {
        return AlternativeUiTextProvider.get(BUILD_BUTTON_TEXT, owner, Messages.ParametersDefinitionProperty_BuildButtonText());
    }

    @Deprecated
    public AbstractProject<?, ?> getOwner() {
        return (AbstractProject) owner;
    }

    @Restricted(NoExternalUse.class) // Jelly
    public ParameterizedJobMixIn.ParameterizedJob getJob() {
        return (ParameterizedJobMixIn.ParameterizedJob) owner;
    }

    @Exported
    public List<ParameterDefinition> getParameterDefinitions() {
        return parameterDefinitions;
    }

    /**
     * Gets the names of all the parameter definitions.
     */
    public List<String> getParameterDefinitionNames() {
        return new DefinitionsAbstractList(this.parameterDefinitions);
    }

    @NonNull
    @Override
    public Collection<Action> getJobActions(Job<?, ?> job) {
        return Set.of(this);
    }

    @Deprecated
    public Collection<Action> getJobActions(AbstractProject<?, ?> job) {
        return getJobActions((Job) job);
    }

    @Deprecated
    public AbstractProject<?, ?> getProject() {
        return (AbstractProject<?, ?>) owner;
    }

    /** @deprecated use {@link #_doBuild(StaplerRequest2, StaplerResponse2, TimeDuration)} */
    @Deprecated
    public void _doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            _doBuild(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), TimeDuration.fromString(req.getParameter("delay")));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    /**
     * Interprets the form submission and schedules a build for a parameterized job.
     *
     * <p>
     * This method is supposed to be invoked from {@link ParameterizedJobMixIn#doBuild(StaplerRequest2, StaplerResponse2, TimeDuration)}.
     */
    public void _doBuild(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        if (delay == null)
            delay = new TimeDuration(TimeUnit.MILLISECONDS.convert(getJob().getQuietPeriod(), TimeUnit.SECONDS));


        List<ParameterValue> values = new ArrayList<>();

        JSONObject formData = req.getSubmittedForm();
        Object parameter = formData.get("parameter");
        if (parameter != null) {
            JSONArray a = JSONArray.fromObject(parameter);

            for (Object o : a) {
                JSONObject jo = (JSONObject) o;
                String name = jo.getString("name");

                ParameterDefinition d = getParameterDefinition(name);
                if (d == null)
                    throw new IllegalArgumentException("No such parameter definition: " + name);
                ParameterValue parameterValue = d.createValue(req, jo);
                if (parameterValue != null) {
                    values.add(parameterValue);
                } else {
                    throw new IllegalArgumentException("Cannot retrieve the parameter value: " + name);
                }
            }
        }

        WaitingItem item = Jenkins.get().getQueue().schedule(
                getJob(), delay.getTimeInSeconds(), new ParametersAction(values), new CauseAction(new Cause.UserIdCause()));
        if (item != null) {
            String url = formData.optString("redirectTo");
            if (url == null || !Util.isSafeToRedirectTo(url))   // avoid open redirect
                url = req.getContextPath() + '/' + item.getUrl();
            rsp.sendRedirect(formData.optInt("statusCode", SC_CREATED), url);
        } else
            // send the user back to the job top page.
            rsp.sendRedirect(".");
    }

    /** @deprecated use {@link #buildWithParameters(StaplerRequest2, StaplerResponse2, TimeDuration)} */
    @Deprecated
    public void buildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            buildWithParameters(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp), TimeDuration.fromString(req.getParameter("delay")));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    public void buildWithParameters(StaplerRequest2 req, StaplerResponse2 rsp, @CheckForNull TimeDuration delay) throws IOException, ServletException {
        List<ParameterValue> values = new ArrayList<>();
        for (ParameterDefinition d : parameterDefinitions) {
            ParameterValue value = d.createValue(req);
            if (value != null) {
                values.add(value);
            }
        }
        if (delay == null)
            delay = new TimeDuration(TimeUnit.MILLISECONDS.convert(getJob().getQuietPeriod(), TimeUnit.SECONDS));

        ScheduleResult scheduleResult = Jenkins.get().getQueue().schedule2(
                getJob(), delay.getTimeInSeconds(), new ParametersAction(values), ParameterizedJobMixIn.getBuildCause(getJob(), req));
        Queue.Item item = scheduleResult.getItem();

        if (item != null && !scheduleResult.isCreated()) {
            rsp.sendRedirect(SC_SEE_OTHER, req.getContextPath() + '/' + item.getUrl());
            return;
        }
        if (item != null) {
            rsp.sendRedirect(SC_CREATED, req.getContextPath() + '/' + item.getUrl());
            return;
        }
        rsp.sendRedirect(".");
    }

    /**
     * Gets the {@link ParameterDefinition} of the given name, if any.
     */
    @CheckForNull
    public ParameterDefinition getParameterDefinition(String name) {
        for (ParameterDefinition pd : parameterDefinitions)
            if (pd.getName().equals(name))
                return pd;
        return null;
    }

    @Extension
    @Symbol("parameters")
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {
        @Override
        public ParametersDefinitionProperty newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
            ParametersDefinitionProperty prop = (ParametersDefinitionProperty) super.newInstance(req, formData);
            if (prop != null && prop.parameterDefinitions.isEmpty()) {
                return null;
            }
            return prop;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return ParameterizedJobMixIn.ParameterizedJob.class.isAssignableFrom(jobType);
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ParametersDefinitionProperty_DisplayName();
        }
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }

    private static class DefinitionsAbstractList extends AbstractList<String> {
        private final List<ParameterDefinition> parameterDefinitions;

        DefinitionsAbstractList(List<ParameterDefinition> parameterDefinitions) {
            this.parameterDefinitions = parameterDefinitions;
        }

        @Override
        public String get(int index) {
            return this.parameterDefinitions.get(index).getName();
        }

        @Override
        public int size() {
            return this.parameterDefinitions.size();
        }
    }
}
