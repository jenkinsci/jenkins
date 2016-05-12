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
package hudson.scm;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;

/**
 * No {@link SCM}.
 *
 * @author Kohsuke Kawaguchi
 */
public class NullSCM extends SCM {
    @Override public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        return null;
    }

    @Override public PollingResult compareRemoteRevisionWith(Job<?,?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        return PollingResult.NO_CHANGES;
    }

    @Override public void checkout(Run<?,?> build, Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
        if (changelogFile != null) {
            createEmptyChangeLog(changelogFile, listener, "log");
        }
    }

    @Override public ChangeLogParser createChangeLogParser() {
        return NullChangeLogParser.INSTANCE;
    }

    @Extension(ordinal = Integer.MAX_VALUE) @Symbol("none")
    public static class DescriptorImpl extends SCMDescriptor<NullSCM> {
        public DescriptorImpl() {
            super(null);
        }

        @Override public String getDisplayName() {
            return Messages.NullSCM_DisplayName();
        }

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new NullSCM();
        }
    }
}
