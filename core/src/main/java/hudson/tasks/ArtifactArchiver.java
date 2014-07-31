/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Brian Westrich, Jean-Baptiste Quenot
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
package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;
import hudson.util.FormValidation;
import java.io.File;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.sf.json.JSONObject;
import javax.annotation.Nonnull;

/**
 * Copies the artifacts into an archive directory.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArtifactArchiver extends Recorder {

    /**
     * Comma- or space-separated list of patterns of files/directories to be archived.
     */
    private final String artifacts;

    /**
     * Possibly null 'excludes' pattern as in Ant.
     */
    private final String excludes;

    /**
     * Just keep the last successful artifact set, no more.
     */
    private final boolean latestOnly;

    /**
     * Fail (or not) the build if archiving returns nothing.
     */
    @Nonnull
    private Boolean allowEmptyArchive;

    public ArtifactArchiver(String artifacts, String excludes, boolean latestOnly) {
        this(artifacts, excludes, latestOnly, false);
    }

    @DataBoundConstructor
    public ArtifactArchiver(String artifacts, String excludes, boolean latestOnly, boolean allowEmptyArchive) {
        this.artifacts = artifacts.trim();
        this.excludes = Util.fixEmptyAndTrim(excludes);
        this.latestOnly = latestOnly;
        this.allowEmptyArchive = allowEmptyArchive;
    }

    // Backwards compatibility for older builds
    public Object readResolve() {
        if (allowEmptyArchive == null) {
            this.allowEmptyArchive = Boolean.getBoolean(ArtifactArchiver.class.getName()+".warnOnEmpty");
        }
        return this;
    }

    public String getArtifacts() {
        return artifacts;
    }

    public String getExcludes() {
        return excludes;
    }

    public boolean isLatestOnly() {
        return latestOnly;
    }

    public boolean getAllowEmptyArchive() {
        return allowEmptyArchive;
    }
    
    private void listenerWarnOrError(BuildListener listener, String message) {
    	if (allowEmptyArchive) {
    		listener.getLogger().println(String.format("WARN: %s", message));
    	} else {
    		listener.error(message);
    	}
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        if(artifacts.length()==0) {
            listener.error(Messages.ArtifactArchiver_NoIncludes());
            build.setResult(Result.FAILURE);
            return true;
        }

        listener.getLogger().println(Messages.ArtifactArchiver_ARCHIVING_ARTIFACTS());
        try {
            FilePath ws = build.getWorkspace();
            if (ws==null) { // #3330: slave down?
                return true;
            }

            String artifacts = build.getEnvironment(listener).expand(this.artifacts);

            Map<String,String> files = ws.act(new ListFiles(artifacts, excludes));
            if (!files.isEmpty()) {
                build.pickArtifactManager().archive(ws, launcher, listener, files);
            } else {
                Result result = build.getResult();
                if (result != null && result.isBetterOrEqualTo(Result.UNSTABLE)) {
                    // If the build failed, don't complain that there was no matching artifact.
                    // The build probably didn't even get to the point where it produces artifacts. 
                    listenerWarnOrError(listener, Messages.ArtifactArchiver_NoMatchFound(artifacts));
                    String msg = null;
                    try {
                    	msg = ws.validateAntFileMask(artifacts);
                    } catch (Exception e) {
                    	listenerWarnOrError(listener, e.getMessage());
                    }
                    if(msg!=null)
                        listenerWarnOrError(listener, msg);
                }
                if (!allowEmptyArchive) {
                	build.setResult(Result.FAILURE);
                }
                return true;
            }
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace(listener.error(
                    Messages.ArtifactArchiver_FailedToArchive(artifacts)));
            build.setResult(Result.FAILURE);
            return true;
        }

        return true;
    }

    private static final class ListFiles implements FilePath.FileCallable<Map<String,String>> {
        private static final long serialVersionUID = 1;
        private final String includes, excludes;
        ListFiles(String includes, String excludes) {
            this.includes = includes;
            this.excludes = excludes;
        }
        @Override public Map<String,String> invoke(File basedir, VirtualChannel channel) throws IOException, InterruptedException {
            Map<String,String> r = new HashMap<String,String>();
            for (String f : Util.createFileSet(basedir, includes, excludes).getDirectoryScanner().getIncludedFiles()) {
                f = f.replace(File.separatorChar, '/');
                r.put(f, f);
            }
            return r;
        }
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        if(latestOnly) {
            AbstractBuild<?,?> b = build.getProject().getLastCompletedBuild();
            Result bestResultSoFar = Result.NOT_BUILT;
            while(b!=null) {
                if(b.getResult()!=null){
                    if (b.getResult().isBetterThan(bestResultSoFar)) {
                        bestResultSoFar = b.getResult();
                    } else {
                        // remove old artifacts
                        try {
                            if (b.getArtifactManager().delete()) {
                                listener.getLogger().println(Messages.ArtifactArchiver_DeletingOld(b.getDisplayName()));
                            }
                        } catch (IOException e) {
                            e.printStackTrace(listener.error(e.getMessage()));
                        } catch (InterruptedException x) {
                            x.printStackTrace(listener.error(x.getMessage()));
                        }
                    }
                }
                b = b.getPreviousBuild();
            }
        }
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
    
    /**
     * @deprecated as of 1.286
     *      Some plugin depends on this, so this field is left here and points to the last created instance.
     *      Use {@link jenkins.model.Jenkins#getDescriptorByType(Class)} instead.
     */
    public static volatile DescriptorImpl DESCRIPTOR;

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            DESCRIPTOR = this; // backward compatibility
        }

        public String getDisplayName() {
            return Messages.ArtifactArchiver_DisplayName();
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public FormValidation doCheckArtifacts(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            return FilePath.validateFileMask(project.getSomeWorkspace(),value);
        }

        @Override
        public ArtifactArchiver newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(ArtifactArchiver.class,formData);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
