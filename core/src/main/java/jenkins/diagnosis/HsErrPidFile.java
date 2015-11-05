package jenkins.diagnosis;

import hudson.Util;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * Serves hs_err_pid file.
 *
 * @author Kohsuke Kawaguchi
 */
public class HsErrPidFile {
    private final HsErrPidList owner;
    private final File file;

    public HsErrPidFile(HsErrPidList owner, File file) {
        this.owner = owner;
        this.file = file;
    }

    public String getName() {
        return file.getName();
    }

    public String getPath() {
        return file.getPath();
    }

    public long getLastModified() {
        return file.lastModified();
    }

    public Date getLastModifiedDate() {
        return new Date(file.lastModified());
    }

    public String getTimeSpanString() {
        return Util.getTimeSpanString(System.currentTimeMillis()-getLastModified());
    }

    public HttpResponse doDownload() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        return HttpResponses.staticResource(file);
    }

    public HttpResponse doDelete() throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        file.delete();
        owner.files.remove(this);
        return HttpResponses.redirectTo("../..");
    }
}
