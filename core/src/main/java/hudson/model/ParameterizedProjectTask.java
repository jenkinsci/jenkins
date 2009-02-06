/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import hudson.model.Queue.Executable;
import hudson.util.QueueTaskFilter;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * A task representing a project that should be built with a certain set of
 * parameter values
 */
public class ParameterizedProjectTask extends QueueTaskFilter {

    private final AbstractProject<?,?> project;
    private final List<ParameterValue> parameters;

    private static long COUNTER = System.currentTimeMillis(); 

    /**
     * Used for identifying the task in the queue
     */
    private final String key = Long.toString(COUNTER++);

    public ParameterizedProjectTask(AbstractProject<?,?> project, List<ParameterValue> parameters) {
        super(project);
        this.project = project;
        this.parameters = parameters;
    }
    
    public AbstractProject<?, ?> getProject() {
		return project;
	}

	public List<ParameterValue> getParameters() {
		return parameters;
	}

	@Override
    public Executable createExecutable() throws IOException {
        AbstractBuild<?, ?> build = project.createExecutable();
        build.addAction(new ParametersAction(parameters, build));

        return build;
    }
	
    /**
     * Cancels a scheduled build.
     */
    public void doCancelQueue( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        project.checkPermission(AbstractProject.BUILD);

        Hudson.getInstance().getQueue().cancel(this);
        rsp.forwardToPreviousPage(req);
    }
	

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((parameters == null) ? 0 : parameters.hashCode());
        result = prime * result + ((project == null) ? 0 : project.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ParameterizedProjectTask other = (ParameterizedProjectTask) obj;
        if (parameters == null) {
            if (other.parameters != null)
                return false;
        } else if (!parameters.equals(other.parameters)) {
            return false;
        }
        if (project != other.project) {
            return false;
        }
        return true;
    }
    
    public String getUrl() {
    	return getProject().getUrl() + "/parameters/queued/" + key + "/";
    }

	public String getQueueKey() {
		return key;
	}
}
