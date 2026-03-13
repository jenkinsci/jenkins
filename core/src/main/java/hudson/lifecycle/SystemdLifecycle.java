package hudson.lifecycle;

import com.sun.jna.Library;
import com.sun.jna.Native;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link Lifecycle} that delegates its responsibility to {@code systemd(1)}.
 *
 * @author Basil Crow
 */
@Restricted(NoExternalUse.class)
public class SystemdLifecycle extends ExitLifecycle {

    private static final Logger LOGGER = Logger.getLogger(SystemdLifecycle.class.getName());

    interface Systemd extends Library {
        Systemd INSTANCE = Native.load("systemd", Systemd.class);

        int sd_notify(int unset_environment, String state);
    }

    @Override
    public void onReady() {
        super.onReady();
        notify("READY=1");
    }

    @Override
    public void onReload(@NonNull String user, @CheckForNull String remoteAddr) {
        super.onReload(user, remoteAddr);
        notify("RELOADING=1");
    }

    @Override
    public void onStop(@NonNull String user, @CheckForNull String remoteAddr) {
        super.onStop(user, remoteAddr);
        notify("STOPPING=1");
    }

    @Override
    public void onExtendTimeout(long timeout, @NonNull TimeUnit unit) {
        super.onExtendTimeout(timeout, unit);
        notify(String.format("EXTEND_TIMEOUT_USEC=%d", unit.toMicros(timeout)));
    }

    @Override
    public void onStatusUpdate(String status) {
        super.onStatusUpdate(status);
        notify(String.format("STATUS=%s", status));
    }

    private static synchronized void notify(String message) {
        int rv = Systemd.INSTANCE.sd_notify(0, message);
        if (rv < 0) {
            LOGGER.log(Level.WARNING, "sd_notify(3) returned {0}", rv);
        }
    }
}
