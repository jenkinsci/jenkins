package hudson.tasks;

import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Fingerprint;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Fingerprint.BuildPtr;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records fingerprints of the specified files.
 *
 * @author Kohsuke Kawaguchi
 */
public class Fingerprinter extends Publisher {

    /**
     * Comma-separated list of files/directories to be fingerprinted.
     */
    private final String targets;

    /**
     * Also record all the finger prints of the build artifacts.
     */
    private final boolean recordBuildArtifacts;

    public Fingerprinter(String targets, boolean recordBuildArtifacts) {
        this.targets = targets;
        this.recordBuildArtifacts = recordBuildArtifacts;
    }

    public String getTargets() {
        return targets;
    }

    public boolean getRecordBuildArtifacts() {
        return recordBuildArtifacts;
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) {
        listener.getLogger().println("Recording fingerprints");

        Map<String,String> record = new HashMap<String,String>();

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            // I don't think this is possible, but check anyway
            e.printStackTrace(listener.error("MD5 not installed"));
            build.setResult(Result.FAILURE);
            return true;
        }

        if(targets.length()!=0)
            record(build, md5, listener, record, targets);

        if(recordBuildArtifacts) {
            ArtifactArchiver aa = (ArtifactArchiver) build.getProject().getPublishers().get(ArtifactArchiver.DESCRIPTOR);
            if(aa==null) {
                // configuration error
                listener.error("Build artifacts are supposed to be fingerprinted, but build artifact archiving is not configured");
                build.setResult(Result.FAILURE);
                return true;
            }
            record(build, md5, listener, record, aa.getArtifacts() );
        }

        build.getActions().add(new FingerprintAction(build,record));

        return true;
    }

    private void record(Build build, MessageDigest md5, BuildListener listener, Map<String,String> record, String targets) {
        Project p = build.getProject();

        FileSet src = new FileSet();
        File baseDir = p.getWorkspace().getLocal();
        src.setDir(baseDir);
        src.setIncludes(targets);

        byte[] buf = new byte[8192];

        DirectoryScanner ds = src.getDirectoryScanner(new org.apache.tools.ant.Project());
        for( String f : ds.getIncludedFiles() ) {
            File file = new File(baseDir,f);

            // consider the file to be produced by this build only if the timestamp
            // is newer than when the build has started.
            boolean produced = build.getTimestamp().getTimeInMillis() <= file.lastModified();

            try {
                md5.reset();    // technically not necessary, but hey, just to be safe
                DigestInputStream in =new DigestInputStream(new FileInputStream(file),md5);
                try {
                    while(in.read(buf)>0)
                        ; // simply discard the input
                } finally {
                    in.close();
                }

                Fingerprint fp = Hudson.getInstance().getFingerprintMap().getOrCreate(
                    produced?build:null, file.getName(), md5.digest());
                if(fp==null) {
                    listener.error("failed to record fingerprint for "+file);
                    continue;
                }
                fp.add(build);
                record.put(f,fp.getHashString());
            } catch (IOException e) {
                e.printStackTrace(listener.error("Failed to compute digest for "+file));
            }
        }
    }

    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }


    public static final Descriptor<Publisher> DESCRIPTOR = new Descriptor<Publisher>(Fingerprinter.class) {
        public String getDisplayName() {
            return "Record fingerprints of files to track usage";
        }

        public String getHelpFile() {
            return "/help/project-config/fingerprint.html";
        }

        public Publisher newInstance(StaplerRequest req) {
            return new Fingerprinter(
                req.getParameter("fingerprint_targets").trim(),
                req.getParameter("fingerprint_artifacts")!=null);
        }
    };


    /**
     * Action for displaying fingerprints.
     */
    public static final class FingerprintAction implements Action {
        private final Build build;

        private final Map<String,String> record;

        private transient WeakReference<Map<String,Fingerprint>> ref;

        public FingerprintAction(Build build, Map<String, String> record) {
            this.build = build;
            this.record = record;
        }

        public String getIconFileName() {
            return "fingerprint.gif";
        }

        public String getDisplayName() {
            return "See fingerprints";
        }

        public String getUrlName() {
            return "fingerprints";
        }

        public Build getBuild() {
            return build;
        }

        /**
         * Map from file names of the fingeprinted file to its fingerprint record.
         */
        public synchronized Map<String,Fingerprint> getFingerprints() {
            if(ref!=null) {
                Map<String,Fingerprint> m = ref.get();
                if(m!=null)
                    return m;
            }

            Hudson h = Hudson.getInstance();

            Map<String,Fingerprint> m = new TreeMap<String,Fingerprint>();
            for (Entry<String, String> r : record.entrySet()) {
                try {
                    m.put(r.getKey(), h._getFingerprint(r.getValue()) );
                } catch (IOException e) {
                    logger.log(Level.WARNING,e.getMessage(),e);
                }
            }

            m = Collections.unmodifiableMap(m);
            ref = new WeakReference<Map<String,Fingerprint>>(m);
            return m;
        }

        /**
         * Gets the dependency to other builds in a map.
         * Returns build numbers instead of {@link Build}, since log records may be gone.
         */
        public Map<Project,Integer> getDependencies() {
            Map<Project,Integer> r = new HashMap<Project,Integer>();

            for (Fingerprint fp : getFingerprints().values()) {
                BuildPtr bp = fp.getOriginal();
                if(bp==null)    continue;       // outside Hudson
                if(bp.is(build))    continue;   // we are the owner

                Integer existing = r.get(bp.getJob());
                if(existing!=null && existing>bp.getNumber())
                    continue;   // the record in the map is already up to date
                r.put((Project)bp.getJob(),bp.getNumber());
            }
            
            return r;
        }
    }

    private static final Logger logger = Logger.getLogger(Fingerprinter.class.getName());
}
