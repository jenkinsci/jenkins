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

import hudson.Extension;
import hudson.Util;
import hudson.model.Queue.WaitingItem;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import jenkins.model.Jenkins;
import jenkins.model.OptionalJobProperty;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.util.TimeDuration;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Keeps a list of the parameters defined for a project.
 *
 * <p>
 * This class also implements {@link Action} so that <tt>index.jelly</tt> provides
 * a form to enter build parameters.
 * <p>The owning job needs a {@code sidepanel.jelly} and should have web methods delegating to {@link ParameterizedJobMixIn#doBuild} and {@link ParameterizedJobMixIn#doBuildWithParameters}.
 * The builds also need a {@code sidepanel.jelly}.
 */
@ExportedBean(defaultVisibility=2)
public class ParametersDefinitionProperty extends OptionalJobProperty<Job<?, ?>>
        implements Action {

    private List<ParameterDefinition> parameterDefinitions;

    @DataBoundConstructor
    public ParametersDefinitionProperty(@CheckForNull List<ParameterDefinition> parameterDefinitions) {
        this.parameterDefinitions = (parameterDefinitions != null) ? parameterDefinitions : Collections.<ParameterDefinition>emptyList();
    }

    public ParametersDefinitionProperty(@CheckForNull ParameterDefinition... parameterDefinitions) {
        this(parameterDefinitions != null ? Arrays.asList(parameterDefinitions) : null);
    }

    protected Object readResolve() {
        if (parameterDefinitions == null) {
            parameterDefinitions = Collections.emptyList();
        }
        return this;
    }

    @Deprecated
    public AbstractProject<?,?> getOwner() {
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
        return new AbstractList<String>() {
            public String get(int index) {
                if (parameterDefinitions.get(index) != null) {
                    return parameterDefinitions.get(index).getName();
                } else {
                    return null;
                }
            }

            public int size() {
                return parameterDefinitions.size();
            }
        };
    }

    @Nonnull
    @Override
    public Collection<Action> getJobActions(Job<?, ?> job) {
        return Collections.<Action>singleton(this);
    }

    @Deprecated
    public Collection<Action> getJobActions(AbstractProject<?, ?> job) {
        return getJobActions((Job) job);
    }

    @Deprecated
    public AbstractProject<?, ?> getProject() {
        return (AbstractProject<?, ?>) owner;
    }

    /** @deprecated use {@link #_doBuild(StaplerRequest, StaplerResponse, TimeDuration)} */
    @Deprecated
    public void _doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        _doBuild(req, rsp, TimeDuration.fromString(req.getParameter("delay")));
    }

    /**
     * Interprets the form submission and schedules a build for a parameterized job.
     *
     * <p>
     * This method is supposed to be invoked from {@link ParameterizedJobMixIn#doBuild(StaplerRequest, StaplerResponse, TimeDuration)}.
     */
    public void _doBuild(StaplerRequest req, StaplerResponse rsp, @QueryParameter TimeDuration delay) throws IOException, ServletException {
        if (delay==null)    delay=new TimeDuration(getJob().getQuietPeriod());


        List<ParameterValue> values = new ArrayList<ParameterValue>();
        
        JSONObject formData = req.getSubmittedForm();
        JSONArray a = JSONArray.fromObject(formData.get("parameter"));

        for (Object o : a) {
            JSONObject jo = (JSONObject) o;
            String name = jo.getString("name");

            ParameterDefinition d = getParameterDefinition(name);
            if(d==null)
                throw new IllegalArgumentException("No such parameter definition: " + name);
            ParameterValue parameterValue = d.createValue(req, jo);
            if (parameterValue != null) {
                values.add(parameterValue);
            } else {
                throw new IllegalArgumentException("Cannot retrieve the parameter value: " + name);
            }
        }

    	WaitingItem item = Jenkins.getInstance().getQueue().schedule(
                getJob(), delay.getTime(), new ParametersAction(values), new CauseAction(new Cause.UserIdCause()));
        if (item!=null) {
            String url = formData.optString("redirectTo");
            if (url==null || Util.isAbsoluteUri(url))   // avoid open redirect
                url = req.getContextPath()+'/'+item.getUrl();
            rsp.sendRedirect(formData.optInt("statusCode",SC_CREATED), url);
        } else
            // send the user back to the job top page.
            rsp.sendRedirect(".");
    }

    /** @deprecated use {@link #buildWithParameters(StaplerRequest, StaplerResponse, TimeDuration)} */
    @Deprecated
    public void buildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        buildWithParameters(req,rsp,TimeDuration.fromString(req.getParameter("delay")));
    }

    public void buildWithParameters(StaplerRequest req, StaplerResponse rsp, @CheckForNull TimeDuration delay) throws IOException, ServletException {
        List<ParameterValue> values = new ArrayList<ParameterValue>();
        for (ParameterDefinition d: parameterDefinitions) {
        	ParameterValue value = d.createValue(req);
        	if (value != null) {
        		values.add(value);
        	}
        }
        if (delay==null)    delay=new TimeDuration(getJob().getQuietPeriod());

        Queue.Item item = Jenkins.getInstance().getQueue().schedule2(
                getJob(), delay.getTime(), new ParametersAction(values), ParameterizedJobMixIn.getBuildCause(getJob(), req)).getItem();

        if (item != null) {
            rsp.sendRedirect(SC_CREATED, req.getContextPath() + '/' + item.getUrl());
        } else {
            rsp.sendRedirect(".");
        }
    }

    /**
     * Gets the {@link ParameterDefinition} of the given name, if any.
     */
    public ParameterDefinition getParameterDefinition(String name) {
        for (ParameterDefinition pd : parameterDefinitions)
            if (pd.getName().equals(name))
                return pd;
        return null;
    }

    @Extension
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {
        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return ParameterizedJobMixIn.ParameterizedJob.class.isAssignableFrom(jobType);
        }

        @Override
        public String getDisplayName() {
            return Messages.ParametersDefinitionProperty_DisplayName();
        }
    }

    public String getDisplayName() {
        return null;
    }

    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }
}
