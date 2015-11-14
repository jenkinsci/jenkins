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
import jenkins.MasterToSlaveFileCallable;
import hudson.Launcher;
import hudson.Util;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.remoting.VirtualChannel;
import hudson.util.FormValidation;
import java.io.File;

import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;
import javax.annotation.Nonnull;
import jenkins.model.BuildDiscarder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.BuildListenerAdapter;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Copies the artifacts into an archive directory.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArtifactArchiver extends Recorder implements SimpleBuildStep {

    private static final Logger LOG = Logger.getLogger(ArtifactArchiver.class.getName());

    /**
     * Comma- or space-separated list of patterns of files/directories to be archived.
     */
    private String artifacts;

    /**
     * Possibly null 'excludes' pattern as in Ant.
     */
    private String excludes = "";

    @Deprecated
    private Boolean latestOnly;

    /**
     * Fail (or not) the build if archiving returns nothing.
     */
    @Nonnull
    private Boolean allowEmptyArchive;

    /**
     * Archive only if build is successful, skip archiving on failed builds.
     */
    private boolean onlyIfSuccessful;

    /**
     * Archive only if build is not successful, skip archiving on successful builds.
     */
    private boolean onlyIfNotSuccessful;

    private boolean fingerprint;

    /**
     * Default ant exclusion
     */
    @Nonnull
    private Boolean defaultExcludes = true;
    
    /**
     * Indicate whether include and exclude patterns should be considered as case sensitive
     */
    @Nonnull
    private Boolean caseSensitive = true;

    @DataBoundConstructor public ArtifactArchiver(String artifacts) {
        this.artifacts = artifacts.trim();
        allowEmptyArchive = false;
    }

    @Deprecated
    public ArtifactArchiver(String artifacts, String excludes, boolean latestOnly) {
        this(artifacts, excludes, latestOnly, false, false);
    }

    @Deprecated
    public ArtifactArchiver(String artifacts, String excludes, boolean latestOnly, boolean allowEmptyArchive) {
        this(artifacts, excludes, latestOnly, allowEmptyArchive, false);
    }

    @Deprecated
    public ArtifactArchiver(String artifacts, String excludes, boolean latestOnly, boolean allowEmptyArchive, boolean onlyIfSuccessful) {
        this(artifacts, excludes , latestOnly , allowEmptyArchive, onlyIfSuccessful , true);
    }

    @Deprecated
    public ArtifactArchiver(String artifacts, String excludes, boolean latestOnly, boolean allowEmptyArchive, boolean onlyIfSuccessful, Boolean defaultExcludes) {
        this(artifacts);
        setExcludes(excludes);
        this.latestOnly = latestOnly;
        setAllowEmptyArchive(allowEmptyArchive);
        setOnlyIfSuccessful(onlyIfSuccessful);
        setDefaultExcludes(defaultExcludes);
    }

    // Backwards compatibility for older builds
    public Object readResolve() {
        if (allowEmptyArchive == null) {
            this.allowEmptyArchive = Boolean.getBoolean(ArtifactArchiver.class.getName()+".warnOnEmpty");
        }
        if (defaultExcludes == null){
            defaultExcludes = true;
        }
        if (caseSensitive == null) {
            caseSensitive = true;
        }
        return this;
    }

    public String getArtifacts() {
        return artifacts;
    }

    public String getExcludes() {
        return excludes;
    }

    @DataBoundSetter public final void setExcludes(String excludes) {
        this.excludes = Util.fixEmptyAndTrim(excludes);
    }

    @Deprecated
    public boolean isLatestOnly() {
        return latestOnly != null ? latestOnly : false;
    }

    public boolean isOnlyIfSuccessful() {
        return onlyIfSuccessful;
    }

    public boolean isOnlyIfNotSuccessful() {
        return onlyIfNotSuccessful;
    }

    @DataBoundSetter public final void setOnlyIfSuccessful(boolean onlyIfSuccessful) {
        this.onlyIfSuccessful = onlyIfSuccessful;
		if (this.onlyIfSuccessful == true) {
			this.onlyIfNotSuccessful = false;
		}
    }

    @DataBoundSetter public final void setOnlyIfNotSuccessful(boolean onlyIfNotSuccessful) {
        this.onlyIfNotSuccessful = onlyIfNotSuccessful;
		if (this.onlyIfNotSuccessful == true) {
			this.onlyIfSuccessful = false;
		}
    }

    public boolean isFingerprint() {
        return fingerprint;
    }

    /** Whether to fingerprint the artifacts after we archive them. */
    @DataBoundSetter public void setFingerprint(boolean fingerprint) {
        this.fingerprint = fingerprint;
    }

    public boolean getAllowEmptyArchive() {
        return allowEmptyArchive;
    }

    @DataBoundSetter public final void setAllowEmptyArchive(boolean allowEmptyArchive) {
        this.allowEmptyArchive = allowEmptyArchive;
    }

    public boolean isDefaultExcludes() {
        return defaultExcludes;
    }

    @DataBoundSetter public final void setDefaultExcludes(boolean defaultExcludes) {
        this.defaultExcludes = defaultExcludes;
    }
    
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    @DataBoundSetter public final void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    private void listenerWarnOrError(TaskListener listener, String message) {
    	if (allowEmptyArchive) {
    		listener.getLogger().println(String.format("WARN: %s", message));
    	} else {
    		listener.error(message);
    	}
    }

    @Override
    public void perform(Run<?,?> build, FilePath ws, Launcher launcher, TaskListener listener) throws InterruptedException {
        if(artifacts.length()==0) {
            listener.error(Messages.ArtifactArchiver_NoIncludes());
            build.setResult(Result.FAILURE);
            return;
        }

		// Determine if we want to archive the logs:
		// 1. If onlySuccessful is set and the build result is successful or
		// 2. If onlyNotSuccessful is set and the build result is a failure
        if ((onlyIfSuccessful && build.getResult() != null && build.getResult().isWorseThan(Result.UNSTABLE)) ||
            (onlyIfNotSuccessful && build.getResult() != null && build.getResult() == Result.FAILURE)) {
			if (this.onlyIfSuccessful) {
				listener.getLogger().println(Messages.ArtifactArchiver_SkipBecauseOnlyIfSuccessful());
			} else {
				listener.getLogger().println(Messages.ArtifactArchiver_SkipBecauseOnlyIfNotSuccessful());
			}
            return;
        }

        listener.getLogger().println(Messages.ArtifactArchiver_ARCHIVING_ARTIFACTS());
        try {
            String artifacts = build.getEnvironment(listener).expand(this.artifacts);

            Map<String,String> files = ws.act(new ListFiles(artifacts, excludes, defaultExcludes, caseSensitive));
            if (!files.isEmpty()) {
                build.pickArtifactManager().archive(ws, launcher, BuildListenerAdapter.wrap(listener), files);
                if (fingerprint) {
                    new Fingerprinter(artifacts).perform(build, ws, launcher, listener);
                }
            } else {
                Result result = build.getResult();
                if (result != null && result.isBetterOrEqualTo(Result.UNSTABLE)) {
                    // If the build failed, don't complain that there was no matching artifact.
                    // The build probably didn't even get to the point where it produces artifacts. 
                    listenerWarnOrError(listener, Messages.ArtifactArchiver_NoMatchFound(artifacts));
                    String msg = null;
                    try {
                    	msg = ws.validateAntFileMask(artifacts, FilePath.VALIDATE_ANT_FILE_MASK_BOUND, caseSensitive);
                    } catch (Exception e) {
                    	listenerWarnOrError(listener, e.getMessage());
                    }
                    if(msg!=null)
                        listenerWarnOrError(listener, msg);
                }
                if (!allowEmptyArchive) {
                	build.setResult(Result.FAILURE);
                }
                return;
            }
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace(listener.error(
                    Messages.ArtifactArchiver_FailedToArchive(artifacts)));
            build.setResult(Result.FAILURE);
            return;
        }
    }

    private static final class ListFiles extends MasterToSlaveFileCallable<Map<String,String>> {
        private static final long serialVersionUID = 1;
        private final String includes, excludes;
        private final boolean defaultExcludes;
        private final boolean caseSensitive;

        ListFiles(String includes, String excludes, boolean defaultExcludes, boolean caseSensitive) {
            this.includes = includes;
            this.excludes = excludes;
            this.defaultExcludes = defaultExcludes;
            this.caseSensitive = caseSensitive;
        }

        @Override public Map<String,String> invoke(File basedir, VirtualChannel channel) throws IOException, InterruptedException {
            Map<String,String> r = new HashMap<String,String>();

            FileSet fileSet = Util.createFileSet(basedir, includes, excludes);
            fileSet.setDefaultexcludes(defaultExcludes);
            fileSet.setCaseSensitive(caseSensitive);

            for (String f : fileSet.getDirectoryScanner().getIncludedFiles()) {
                f = f.replace(File.separatorChar, '/');
                r.put(f, f);
            }
            return r;
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
    
    /**
     * @deprecated as of 1.286
     *      Some plugin depends on this, so this field is left here and points to the last created instance.
     *      Use {@link jenkins.model.Jenkins#getDescriptorByType(Class)} instead.
     */
    @Deprecated
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
         * Performs on-the-fly validation of the file mask wildcard, when the artifacts
         * textbox or the caseSensitive checkbox are modified
         */
        public FormValidation doCheckArtifacts(@AncestorInPath AbstractProject project,
                @QueryParameter String value,
                @QueryParameter(value = "caseSensitive") String caseSensitive)
                throws IOException {
            if (project == null) {
                return FormValidation.ok();
            }
            // defensive approach to remain case sensitive in doubtful situations
            boolean bCaseSensitive = caseSensitive == null || !"false".equals(caseSensitive);
            return FilePath.validateFileMask(project.getSomeWorkspace(), value, bCaseSensitive);
        }

        @Override
        public ArtifactArchiver newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(ArtifactArchiver.class,formData);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    @Extension public static final class Migrator extends ItemListener {
        @SuppressWarnings("deprecation")
        @Override public void onLoaded() {
            for (AbstractProject<?,?> p : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                try {
                    ArtifactArchiver aa = p.getPublishersList().get(ArtifactArchiver.class);
                    if (aa != null && aa.latestOnly != null) {
                        if (aa.latestOnly) {
                            BuildDiscarder bd = p.getBuildDiscarder();
                            if (bd instanceof LogRotator) {
                                LogRotator lr = (LogRotator) bd;
                                if (lr.getArtifactNumToKeep() == -1) {
                                    p.setBuildDiscarder(new LogRotator(lr.getDaysToKeep(), lr.getNumToKeep(), lr.getArtifactDaysToKeep(), 1));
                                } else {
                                    LOG.log(Level.WARNING, "will not clobber artifactNumToKeep={0} in {1}", new Object[] {lr.getArtifactNumToKeep(), p});
                                }
                            } else if (bd == null) {
                                p.setBuildDiscarder(new LogRotator(-1, -1, -1, 1));
                            } else {
                                LOG.log(Level.WARNING, "unrecognized BuildDiscarder {0} in {1}", new Object[] {bd, p});
                            }
                        }
                        aa.latestOnly = null;
                        p.save();
                    }
                    Fingerprinter f = p.getPublishersList().get(Fingerprinter.class);
                    if (f != null && f.getRecordBuildArtifacts()) {
                        f.recordBuildArtifacts = null;
                        if (aa != null) {
                            aa.setFingerprint(true);
                        }
                        if (f.getTargets().isEmpty()) { // no other reason to be here
                            p.getPublishersList().remove(f);
                        }
                        p.save();
                    }
                } catch (IOException x) {
                    LOG.log(Level.WARNING, "could not migrate " + p, x);
                }
            }
        }
    }

}
