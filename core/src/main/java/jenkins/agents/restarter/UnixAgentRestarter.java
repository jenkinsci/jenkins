package jenkins.agents.restarter;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import jenkins.util.JavaVMArguments;

/**
 * On Unix, restart via exec-ing to itself.
 */
@Extension
public class UnixAgentRestarter extends AgentRestarter {
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
        ProcessHandle.Info info = ProcessHandle.current().info();
        if (info.command().isPresent()) {
            // Java 9+ approach
            return info.command().get();
        }

        // Native approach
        long pid = ProcessHandle.current().pid();
        String name = "/proc/" + pid + "/exe";
        try {
            Path exe = Paths.get(name);
            if (Files.exists(exe)) {
                if (Files.isSymbolicLink(exe)) {
                    return Files.readSymbolicLink(exe).toString();
                } else {
                    return exe.toString();
                }
            }
        } catch (IOException | InvalidPathException | UnsupportedOperationException e) {
            LOGGER.log(FINE, "Failed to resolve " + name, e);
        }

        // Legacy approach of last resort
        return Paths.get(System.getProperty("java.home")).resolve("bin").resolve("java").toString();
    }

    private static final Logger LOGGER = Logger.getLogger(UnixAgentRestarter.class.getName());

    private static final long serialVersionUID = 1L;
}
