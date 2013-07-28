/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts, Geoff Cummings
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

import java.util.Locale;

import hudson.EnvVars;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

public class RunParameterValue extends ParameterValue {

    private final String runId;

    @DataBoundConstructor
    public RunParameterValue(String name, String runId, String description) {
        super(name, description);
        this.runId = runId;
    }

    public RunParameterValue(String name, String runId) {
        super(name, null);
        this.runId = runId;
    }

    public Run getRun() {
        return Run.fromExternalizableId(runId);
    }

    public String getRunId() {
        return runId;
    }
    
    @Exported
    public String getJobName() {
    	return runId.split("#")[0];
    }
    
    @Exported
    public String getNumber() {
    	return runId.split("#")[1];
    }
    

    /**
     * Exposes the name/value as an environment variable.
     */
    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
        Run run = getRun();
        
        String value = Jenkins.getInstance().getRootUrl() + run.getUrl();
        env.put(name, value);

        env.put(name + ".jobName", getJobName());   // undocumented, left for backward compatibility
        env.put(name + "_JOBNAME", getJobName());   // prefer this version

        env.put(name + ".number" , getNumber ());   // same as above
        env.put(name + "_NUMBER" , getNumber ());
        
        env.put(name + "_NAME",  run.getDisplayName());  // since 1.504

        String buildResult = (null == run.getResult()) ? "UNKNOWN" : run.getResult().toString();
        env.put(name + "_RESULT",  buildResult);  // since 1.517

        env.put(name.toUpperCase(Locale.ENGLISH),value); // backward compatibility pre 1.345

    }
    
    @Override
    public String toString() {
    	return "(RunParameterValue) " + getName() + "='" + getRunId() + "'";
    }

    @Override public String getShortDescription() {
        return name + "=" + getRun().getFullDisplayName();
    }

}
