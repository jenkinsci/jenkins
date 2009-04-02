/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.maven;

import hudson.model.AbstractProject;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.Launcher;
import hudson.maven.reporters.MavenArtifactRecord;

import java.io.IOException;

/**
 * {@link Publisher} for Maven projects to deploy artifacts to a Maven repository
 * after the fact.
 *
 * <p>
 * When a build breaks in the middle, this is a convenient way to prevent
 * modules from being deployed partially. This can be combined with promoted builds
 * plugin to deploy artifacts after testing, for example. 
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenRedeployer extends Publisher {
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        MavenArtifactRecord mar = build.getAction(MavenArtifactRecord.class);
        if(mar==null) {
            if(build.getResult().isBetterThan(Result.FAILURE)) {
                listener.getLogger().println("There's no record of artifact information. Is this really a Maven build?");
                build.setResult(Result.FAILURE);
            }
            // failed
            return true;
        }

        listener.getLogger().println("TODO");
        
        return true;
    }

    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return AbstractMavenProject.class.isAssignableFrom(jobType);
        }

        public String getDisplayName() {
            return Messages.MavenRedeployer_DisplayName();
        }
    }
}
