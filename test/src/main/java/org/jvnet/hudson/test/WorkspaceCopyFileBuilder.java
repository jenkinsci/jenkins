/*
 * The MIT License
 *
 * Copyright (c) 2015 Oleg Nenashev.
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
package org.jvnet.hudson.test;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import java.io.IOException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Test Builder, which copies a file from a workspace of another job.
 * Supports {@link AbstractProject}s only.
 * @author Oleg Nenashev
 */
public class WorkspaceCopyFileBuilder extends Builder {
    
    private final String fileName;
    private final String jobName;
    private final int buildNumber;

    public WorkspaceCopyFileBuilder(String fileName, String jobName, int buildNumber) {
        this.fileName = fileName;
        this.jobName = jobName;
        this.buildNumber = buildNumber;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public String getJobName() {
        return jobName;
    }
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("Copying a " + fileName + " from " + jobName + "#" + buildNumber);
        
        Jenkins inst = Jenkins.getInstance();
        AbstractProject<?,?> item = inst.getItemByFullName(jobName, AbstractProject.class);
        if (item == null) {
            throw new AbortException("Cannot find a source job: " + jobName);
        }
        
        AbstractBuild<?,?> sourceBuild = item.getBuildByNumber(buildNumber);
        if (sourceBuild == null) {
            throw new AbortException("Cannot find a source build: " + jobName + "#" + buildNumber);
        }
        
        FilePath sourceWorkspace = sourceBuild.getWorkspace();
        if (sourceWorkspace == null) {
            throw new AbortException("Cannot get the source workspace from " + sourceBuild.getDisplayName());
        }
        
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new IOException("Cannot get the workspace of the build");
        }
        workspace.child(fileName).copyFrom(sourceWorkspace.child(fileName));
        
        return true;
    }
    
    @Override
    public Descriptor<Builder> getDescriptor() {
        return new DescriptorImpl();
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Builder> {
        
        @Override
        public Builder newInstance(StaplerRequest req, JSONObject data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDisplayName() {
            return "Copy a file from the workspace of another build";
        }
    }
}