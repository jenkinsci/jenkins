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

import hudson.EnvVars;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Locale;

public class JobParameterValue extends ParameterValue {
    public final Job job;

    @DataBoundConstructor
    public JobParameterValue(String name, Job job) {
        super(name);
        this.job = job;
    }

    @Override
    public Job getValue() {
        return job;
    }

    /**
     * Exposes the name/value as an environment variable.
     */
    @Override
    public void buildEnvironment(Run<?,?> build, EnvVars env) {
        // TODO: check with Tom if this is really what he had in mind
        env.put(name,job.toString());
        env.put(name.toUpperCase(Locale.ENGLISH),job.toString()); // backward compatibility pre 1.345
    }

    @Override public String getShortDescription() {
        return name + "=" + job.getFullDisplayName();
    }
}
