package hudson.util;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.TaskListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SymlinkTestUtil {

    private SymlinkTestUtil() {}

   // Returns true if symbolic links are supported and usable on this system.
    public static boolean isSymlinkSupported() {
        if (Functions.isWindows()) {
            return false;
        }
        try {
            Path tempDir = Files.createTempDirectory("jenkins-symlink-test");
            FilePath ws = new FilePath(tempDir.toFile());
            FilePath target = ws.child("target");
            FilePath link = ws.child("link");

            target.write("test", "UTF-8");
            link.symlinkTo(target.getName(), TaskListener.NULL);

            return link.exists();
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
