package jenkins.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.util.ProcessTree;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
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
    @NonNull
    public static List<String> current() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        if (info.command().isPresent() && info.arguments().isPresent()) {
            // Java 9+ approach
            List<String> args = new ArrayList<>();
            args.add(info.command().get());
            Stream.of(info.arguments().get()).forEach(args::add);
            return args;
        } else if (Functions.isGlibcSupported()) {
            // Native approach
            int pid = Math.toIntExact(ProcessHandle.current().pid());
            ProcessTree.OSProcess process = ProcessTree.get().get(pid);
            if (process != null) {
                List<String> args = process.getArguments();
                if (!args.isEmpty()) {
                    return args;
                }
            }
        }

        // Legacy approach of last resort
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
