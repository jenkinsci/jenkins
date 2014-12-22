/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import com.google.common.collect.ImmutableMap;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import jenkins.MasterToSlaveFileCallable;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import jenkins.model.DependencyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.DependencyGraph.Dependency;
import hudson.model.Fingerprint;
import hudson.model.Fingerprint.BuildPtr;
import hudson.model.FingerprintMap;
import hudson.model.Job;
import jenkins.model.Jenkins;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.FormValidation;
import hudson.util.PackedMap;
import hudson.util.RunList;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep;

/**
 * Records fingerprints of the specified files.
 *
 * @author Kohsuke Kawaguchi
 */
public class Fingerprinter extends Recorder implements Serializable, DependencyDeclarer, SimpleBuildStep {
    public static boolean enableFingerprintsInDependencyGraph = Boolean.getBoolean(Fingerprinter.class.getName() + ".enableFingerprintsInDependencyGraph");
    
    /**
     * Comma-separated list of files/directories to be fingerprinted.
     */
    private final String targets;

    @Deprecated
    Boolean recordBuildArtifacts;

    @DataBoundConstructor public Fingerprinter(String targets) {
        this.targets = targets;
    }

    @Deprecated
    public Fingerprinter(String targets, boolean recordBuildArtifacts) {
        this(targets);
        this.recordBuildArtifacts = recordBuildArtifacts;
    }

    public String getTargets() {
        return targets;
    }

    @Deprecated
    public boolean getRecordBuildArtifacts() {
        return recordBuildArtifacts != null && recordBuildArtifacts;
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException {
        try {
            listener.getLogger().println(Messages.Fingerprinter_Recording());

            Map<String,String> record = new HashMap<String,String>();
            
            EnvVars environment = build.getEnvironment(listener);
            if(targets.length()!=0) {
                String expandedTargets = environment.expand(targets);
                record(build, workspace, listener, record, expandedTargets);
            }

            FingerprintAction fingerprintAction = build.getAction(FingerprintAction.class);
            if (fingerprintAction != null) {
                fingerprintAction.add(record);
            } else {
                build.addAction(new FingerprintAction(build,record));
            }

            if (enableFingerprintsInDependencyGraph) {
                Jenkins.getInstance().rebuildDependencyGraphAsync();
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error(Messages.Fingerprinter_Failed()));
            build.setResult(Result.FAILURE);
        }

        // failing to record fingerprints is an error but not fatal
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        if (enableFingerprintsInDependencyGraph) {
            RunList builds = owner.getBuilds();
            Set<String> seenUpstreamProjects = new HashSet<String>();

            for ( ListIterator iter = builds.listIterator(); iter.hasNext(); ) {
                Run build = (Run) iter.next();
                for (FingerprintAction action : build.getActions(FingerprintAction.class)) {
                    for (AbstractProject key : action.getDependencies().keySet()) {
                        if (key == owner) {
                            continue;   // Avoid self references
                        }

                        AbstractProject p = key;
                        // TODO is this harmful to call unconditionally, so it would apply also to MavenModule for example?
                        if (key.getClass().getName().equals("hudson.matrix.MatrixConfiguration")) {
                            p = key.getRootProject();
                        }

                        if (seenUpstreamProjects.contains(p.getName())) {
                            continue;
                        }

                        seenUpstreamProjects.add(p.getName());
                        graph.addDependency(new Dependency(p, owner) {
                            @Override
                            public boolean shouldTriggerBuild(AbstractBuild build,
                                                              TaskListener listener,
                                                              List<Action> actions) {
                                // Fingerprints should not trigger builds.
                                return false;
                            }
                        });
                    }
                }
            }
        }
    }

    private void record(Run<?,?> build, FilePath ws, TaskListener listener, Map<String,String> record, final String targets) throws IOException, InterruptedException {
        final class Record implements Serializable {
            final boolean produced;
            final String relativePath;
            final String fileName;
            final String md5sum;

            public Record(boolean produced, String relativePath, String fileName, String md5sum) {
                this.produced = produced;
                this.relativePath = relativePath;
                this.fileName = fileName;
                this.md5sum = md5sum;
            }

            Fingerprint addRecord(Run build) throws IOException {
                FingerprintMap map = Jenkins.getInstance().getFingerprintMap();
                return map.getOrCreate(produced?build:null, fileName, md5sum);
            }

            private static final long serialVersionUID = 1L;
        }

        final long buildTimestamp = build.getTimeInMillis();

        List<Record> records = ws.act(new MasterToSlaveFileCallable<List<Record>>() {
            public List<Record> invoke(File baseDir, VirtualChannel channel) throws IOException {
                List<Record> results = new ArrayList<Record>();

                FileSet src = Util.createFileSet(baseDir,targets);

                DirectoryScanner ds = src.getDirectoryScanner();
                for( String f : ds.getIncludedFiles() ) {
                    File file = new File(baseDir,f);

                    // consider the file to be produced by this build only if the timestamp
                    // is newer than when the build has started.
                    // 2000ms is an error margin since since VFAT only retains timestamp at 2sec precision
                    boolean produced = buildTimestamp <= file.lastModified()+2000;

                    try {
                        results.add(new Record(produced,f,file.getName(),new FilePath(file).digest()));
                    } catch (IOException e) {
                        throw new IOException(Messages.Fingerprinter_DigestFailed(file),e);
                    } catch (InterruptedException e) {
                        throw new IOException(Messages.Fingerprinter_Aborted(),e);
                    }
                }

                return results;
            }
        });

        for (Record r : records) {
            Fingerprint fp = r.addRecord(build);
            if(fp==null) {
                listener.error(Messages.Fingerprinter_FailedFor(r.relativePath));
                continue;
            }
            fp.addFor(build);
            record.put(r.relativePath,fp.getHashString());
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String getDisplayName() {
            return Messages.Fingerprinter_DisplayName();
        }

        @Deprecated
        public FormValidation doCheck(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            return doCheckTargets(project, value);
        }

        public FormValidation doCheckTargets(@AncestorInPath AbstractProject<?,?> project, @QueryParameter String value) throws IOException {
            if (project == null) {
                return FormValidation.ok();
            }
            return FilePath.validateFileMask(project.getSomeWorkspace(),value);
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) {
            return req.bindJSON(Fingerprinter.class, formData);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    /**
     * Action for displaying fingerprints.
     */
    public static final class FingerprintAction implements RunAction2 {

        private transient Run build;
        
        private static final Random rand = new Random();

        /**
         * From file name to the digest.
         */
        private /*almost final*/ PackedMap<String,String> record;

        private transient WeakReference<Map<String,Fingerprint>> ref;

        public FingerprintAction(Run build, Map<String, String> record) {
            this.build = build;
            this.record = compact(record);
        }

        @Deprecated
        public FingerprintAction(AbstractBuild build, Map<String, String> record) {
            this((Run) build, record);
        }

        public void add(Map<String,String> moreRecords) {
            Map<String,String> r = new HashMap<String, String>(record);
            r.putAll(moreRecords);
            record = compact(r);
            ref = null;
        }

        public String getIconFileName() {
            return "fingerprint.png";
        }

        public String getDisplayName() {
            return Messages.Fingerprinter_Action_DisplayName();
        }

        public String getUrlName() {
            return "fingerprints";
        }

        public Run getRun() {
            return build;
        }

        @Deprecated
        public AbstractBuild getBuild() {
            return build instanceof AbstractBuild ? (AbstractBuild) build : null;
        }

        /**
         * Obtains the raw data.
         */
        public Map<String,String> getRecords() {
            return record;
        }

        @Override public void onLoad(Run<?,?> r) {
            build = r;
            record = compact(record);
        }

        @Override public void onAttached(Run<?,?> r) {
            // for historical reasons this setup is done in the constructor instead
        }

        /** Share data structure with other builds, mainly those of the same job. */
        private PackedMap<String,String> compact(Map<String,String> record) {
            Map<String,String> b = new HashMap<String,String>();
            for (Entry<String,String> e : record.entrySet()) {
                b.put(e.getKey().intern(), e.getValue().intern());
            }
            return PackedMap.of(b);
        }

        /**
         * Map from file names of the fingerprinted file to its fingerprint record.
         */
        public synchronized Map<String,Fingerprint> getFingerprints() {
            if(ref!=null) {
                Map<String,Fingerprint> m = ref.get();
                if(m!=null)
                    return m;
            }

            Jenkins h = Jenkins.getInstance();

            Map<String,Fingerprint> m = new TreeMap<String,Fingerprint>();
            for (Entry<String, String> r : record.entrySet()) {
                try {
                    Fingerprint fp = h._getFingerprint(r.getValue());
                    if(fp!=null)
                        m.put(r.getKey(), fp);
                } catch (IOException e) {
                    logger.log(Level.WARNING,e.getMessage(),e);
                }
            }

            m = ImmutableMap.copyOf(m);
            ref = new WeakReference<Map<String,Fingerprint>>(m);
            return m;
        }

        /**
         * Gets the dependency to other existing builds in a map.
         */
        public Map<AbstractProject,Integer> getDependencies() {
            return getDependencies(false);
        }
        
        /**
         * Gets the dependency to other builds in a map.
         *
         * @param includeMissing true if the original build should be included in
         *  the result, even if it doesn't exist
         * @since 1.430
         */
        public Map<AbstractProject,Integer> getDependencies(boolean includeMissing) {
            Map<AbstractProject,Integer> r = new HashMap<AbstractProject,Integer>();

            for (Fingerprint fp : getFingerprints().values()) {
                BuildPtr bp = fp.getOriginal();
                if(bp==null)    continue;       // outside Hudson
                if(bp.is(build))    continue;   // we are the owner

                try {
                    Job job = bp.getJob();
                    if (job==null)  continue;   // project no longer exists
                    if (!(job instanceof AbstractProject)) {
                        // Ignoring this for now. In the future we may want a dependency map function not limited to AbstractProject.
                        // (Could be used by getDependencyChanges if pulled up from AbstractBuild into Run, for example.)
                        continue;
                    }
                    if (job.getParent()==build.getParent())
                        continue;   // we are the parent of the build owner, that is almost like we are the owner
                    if(!includeMissing && job.getBuildByNumber(bp.getNumber())==null)
                        continue;               // build no longer exists

                    Integer existing = r.get(job);
                    if(existing!=null && existing>bp.getNumber())
                        continue;   // the record in the map is already up to date
                    r.put((AbstractProject) job, bp.getNumber());
                } catch (AccessDeniedException e) {
                    // Need to log in to access this job, so ignore
                    continue;
                }

            }
            
            return r;
        }
    }

    private static final Logger logger = Logger.getLogger(Fingerprinter.class.getName());

    private static final long serialVersionUID = 1L;
}
