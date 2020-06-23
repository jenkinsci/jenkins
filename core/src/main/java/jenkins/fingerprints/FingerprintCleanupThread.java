package jenkins.fingerprints;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Fingerprint;
import hudson.model.TaskListener;
import jenkins.model.FingerprintFacet;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Symbol("fingerprintCleanup")
@Restricted(NoExternalUse.class)
public class FingerprintCleanupThread extends AsyncPeriodicWork implements ExtensionPoint {

    public FingerprintCleanupThread() {
        super("Fingerprint cleanup");
    }

    public long getRecurrencePeriod() {
        return DAY;
    }

    public static void invoke() {
        getInstance().run();
    }

    private static FingerprintCleanupThread getInstance() {
        return ExtensionList.lookup(AsyncPeriodicWork.class).get(FingerprintCleanupThread.class);
    }

    public void execute(TaskListener taskListener) {
        List<String> fingerprintIds = FingerprintStorage.get().getAllFingerprintIds();
        for (String id : fingerprintIds) {
            try {
                Fingerprint fingerprint = Fingerprint.load(id);
                if (fingerprint != null) {
                    cleanFingerprint(fingerprint,taskListener);
                    fingerprint.save();
                }
            } catch (IOException e) {
                Functions.printStackTrace(e, taskListener.error("Failed to process " + id));
            }
        }

    }

    public static boolean cleanFingerprint(@NonNull Fingerprint fingerprint, TaskListener taskListener) {
        try {
            if (!fingerprint.isAlive() && fingerprint.getFacetBlockingDeletion() == null) {
                taskListener.getLogger().println("deleting obsolete " + fingerprint.toString());
                Fingerprint.delete(fingerprint.getHashString());
                return true;
            } else {
                if (!fingerprint.isAlive()) {
                    FingerprintFacet deletionBlockerFacet = fingerprint.getFacetBlockingDeletion();
                    taskListener.getLogger().println(deletionBlockerFacet.getClass().getName() + " created on " +
                            new Date(deletionBlockerFacet.getTimestamp()) + " blocked deletion of " +
                            fingerprint.getHashString());
                }
                // get the fingerprint in the official map so have the changes visible to Jenkins
                // otherwise the mutation made in FingerprintMap can override our trimming.
                fingerprint = getFingerprint(fingerprint);
                return fingerprint.trim();
            }
        } catch (IOException e) {
            Functions.printStackTrace(e, taskListener.error("Failed to process " + fingerprint.getHashString()));
            return false;
        }
    }

    protected static Fingerprint getFingerprint(Fingerprint fp) throws IOException {
        return Jenkins.get()._getFingerprint(fp.getHashString());
    }

}
