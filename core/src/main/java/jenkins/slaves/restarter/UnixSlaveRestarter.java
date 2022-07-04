package jenkins.slaves.restarter;

import static hudson.util.jna.GNUCLibrary.FD_CLOEXEC;
import static hudson.util.jna.GNUCLibrary.F_GETFD;
import static hudson.util.jna.GNUCLibrary.F_SETFD;
import static hudson.util.jna.GNUCLibrary.LIBC;
import static java.util.logging.Level.FINE;

import com.sun.jna.Native;
import com.sun.jna.StringArray;
import hudson.Extension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.logging.Logger;
import jenkins.util.JavaVMArguments;

/**
 * On Unix, restart via exec-ing to itself.
 */
@Extension
public class UnixSlaveRestarter extends SlaveRestarter {
    private transient List<String> args;

    @Override
    public boolean canWork() {
        try {
            if (File.pathSeparatorChar != ':')
                return false;     // quick test to reject non-Unix without loading all the rest of the classes

            args = JavaVMArguments.current();

            // go through the whole motion to make sure all the relevant classes are loaded now
            LIBC.getdtablesize();
            int v = LIBC.fcntl(99999, F_GETFD);
            LIBC.fcntl(99999, F_SETFD, v);

            getCurrentExecutable();
            LIBC.execv("positively/no/such/executable", new StringArray(new String[]{"a", "b", "c"}));

            return true;
        } catch (UnsupportedOperationException | LinkageError e) {
            LOGGER.log(FINE, getClass() + " unsuitable", e);
            return false;
        }
    }

    @Override
    public void restart() throws Exception {
        // close all files upon exec, except stdin, stdout, and stderr
        int sz = LIBC.getdtablesize();
        for (int i = 3; i < sz; i++) {
            int flags = LIBC.fcntl(i, F_GETFD);
            if (flags < 0) continue;
            LIBC.fcntl(i, F_SETFD, flags | FD_CLOEXEC);
        }

        // exec to self
        String exe = getCurrentExecutable();
        LIBC.execv(exe, new StringArray(args.toArray(new String[0])));
        throw new IOException("Failed to exec '" + exe + "' " + LIBC.strerror(Native.getLastError()));
    }

    /**
     * Gets the current executable name.
     */
    private static String getCurrentExecutable() {
        long pid = ProcessHandle.current().pid();
        String name = "/proc/" + pid + "/exe";
        File exe = new File(name);
        if (exe.exists()) {
            try {
                return Files.readSymbolicLink(exe.toPath()).toString();
            } catch (IOException | InvalidPathException | UnsupportedOperationException e) {
                LOGGER.log(FINE, "Failed to resolve symlink " + exe, e);
            }
            return name;
        }

        // cross-platform fallback
        return System.getProperty("java.home") + "/bin/java";
    }

    private static final Logger LOGGER = Logger.getLogger(UnixSlaveRestarter.class.getName());

    private static final long serialVersionUID = 1L;
}
