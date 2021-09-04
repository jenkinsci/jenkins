package jenkins.util;

import hudson.Functions;
import hudson.util.ProcessTree;
import hudson.util.jna.GNUCLibrary;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * List of arguments for Java VM and application.
 */
@Restricted(NoExternalUse.class)
public class JavaVMArguments {

    /**
     * Gets the process argument list of the current process.
     */
    public static List<String> current() {
        // Native approach
        if (Functions.isGlibcSupported()) {
            int pid = GNUCLibrary.LIBC.getpid();
            ProcessTree.OSProcess process = ProcessTree.get().get(pid);
            if (process != null) {
                List<String> args = process.getArguments();
                if (!args.isEmpty()) {
                    return args;
                }
            }
        }

        // Cross-platform approach
        List<String> args = new ArrayList<>();
        args.add(
                Paths.get(System.getProperty("java.home"))
                        .resolve("bin")
                        .resolve("java")
                        .toString());
        args.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        return args;
    }
}
