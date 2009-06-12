/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jean-Baptiste Quenot, Seiji Sogabe, Tom Huybrechts
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import hudson.Extension;

/**
 * Keeps a list of the parameters defined for a project.
 *
 * <p>
 * This class also implements {@link Action} so that <tt>index.jelly</tt> provides
 * a form to enter build parameters. 
 */
public class ParametersDefinitionProperty extends JobProperty<AbstractProject<?, ?>>
        implements Action {

    private final List<ParameterDefinition> parameterDefinitions;

    public ParametersDefinitionProperty(List<ParameterDefinition> parameterDefinitions) {
        this.parameterDefinitions = parameterDefinitions;
    }

    public ParametersDefinitionProperty(ParameterDefinition... parameterDefinitions) {
        this.parameterDefinitions = Arrays.asList(parameterDefinitions);
    }
    
    public AbstractProject<?,?> getOwner() {
        return owner;
    }

    public List<ParameterDefinition> getParameterDefinitions() {
        return parameterDefinitions;
    }

    @Override
    public Action getJobAction(AbstractProject<?, ?> job) {
        return this;
    }

    public AbstractProject<?, ?> getProject() {
        return (AbstractProject<?, ?>) owner;
    }

    /**
     * Interprets the form submission and schedules a build for a parameterized job.
     *
     * <p>
     * This method is supposed to be invoked from {@link AbstractProject#doBuild(StaplerRequest, StaplerResponse)}.
     */
    public void _doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(!req.getMethod().equals("POST")) {
            // show the parameter entry form.
            req.getView(this,"index.jelly").forward(req,rsp);
            return;
        }

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
            values.add(parameterValue);
        }

    	Hudson.getInstance().getQueue().schedule(
    			owner, 0, new ParametersAction(values), new CauseAction(new Cause.UserCause()));

        // send the user back to the job top page.
        rsp.sendRedirect(".");
    }
    
    public void buildWithParameters(StaplerRequest req, StaplerResponse rsp) throws IOException {
        List<ParameterValue> values = new ArrayList<ParameterValue>();
        for (ParameterDefinition d: parameterDefinitions) {
        	ParameterValue value = d.createValue(req);
        	if (value != null) {
        		values.add(value);
        	} else {
        		throw new IllegalArgumentException("Parameter " + d.getName() + " was missing.");
        	}
        }

    	Hudson.getInstance().getQueue().schedule(
    			owner, 0, new ParametersAction(values), new CauseAction(new Cause.UserCause()));

        // send the user back to the job top page.
        rsp.sendRedirect(".");
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
    public static class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req,
                                          JSONObject formData) throws FormException {
            if (formData.isNullObject()) {
                return null;
            }

            JSONObject parameterized = formData.getJSONObject("parameterized");

            if (parameterized.isNullObject()) {
            	return null;
            }
            
            List<ParameterDefinition> parameterDefinitions = Descriptor.newInstancesFromHeteroList(
                    req, parameterized, "parameter", ParameterDefinition.all());
            if(parameterDefinitions.isEmpty())
                return null;

            return new ParametersDefinitionProperty(parameterDefinitions);
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
        return "parameters";
    }
}
