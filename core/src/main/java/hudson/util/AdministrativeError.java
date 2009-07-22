package hudson.util;

import hudson.model.AdministrativeMonitor;
import hudson.Extension;

/**
 * A convenient {@link AdministrativeMonitor} implementations that show an error message
 * and optional stack trace. This is useful for notifying a non-fatal error to the administrator.
 *
 * <p>
 * These errors are registered when instances are created. No need to use {@link Extension}.
 *
 * @author Kohsuke Kawaguchi
 */
public class AdministrativeError extends AdministrativeMonitor {
    public final String message;
    public final String title;
    public final Throwable details;

    /**
     * @param id
     *      Unique ID that distinguishes this error from other errors.
     *      Must remain the same across Hudson executions. Use a caller class name, or something like that.
     * @param title
     *      A title of the problem. This is used as the HTML title
     *      of the details page. Should be just one sentence, like "ZFS migration error."
     * @param message
     *      A short description of the problem. This is used in the "/manage" page, and can include HTML, but it should be still short.
     * @param details
     *      An exception indicating the problem. The administrator can see this once they click "more details".
     */
    public AdministrativeError(String id, String title, String message, Throwable details) {
        super(id);
        this.message = message;
        this.title = title;
        this.details = details;

        all().add(this);
    }

    public boolean isActivated() {
        return true;
    }
}
