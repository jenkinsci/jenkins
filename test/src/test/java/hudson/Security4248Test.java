package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import org.apache.tools.tar.TarConstants;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

// thread-1 writes to <base>/subdir/file1.txt, thread-2 swaps subdir for a symlink to <outside>
@Issue("SECURITY-4248")
@WithJenkins
class Security4248Test {

    private File base;
    private File outside;
    private File tar;

    @BeforeEach
    void setUp(JenkinsRule r) {
        assumeFalse(Functions.isWindows());
        var home = r.jenkins.getRootDir();
        base = new File(home, "base").getAbsoluteFile();
        outside = new File(home, "outside").getAbsoluteFile();
        tar = new File(home, "crafted.tar");
        assertThat(base.mkdirs(), is(true));
        assertThat(outside.mkdirs(), is(true));
    }

    @AfterEach
    void tearDown() {
        FilePath.afterInitialAncestorCheck = target -> {};
    }

    @Test
    void symlinkSwappedInAfterInitialAncestorCheckIsRejected() throws Exception {
        try (var out = new TarOutputStream(Files.newOutputStream(tar.toPath()))) {
            var content = "data".getBytes(StandardCharsets.UTF_8);
            var file = new TarEntry("subdir/file1.txt", true);
            file.setSize(content.length);
            out.putNextEntry(file);
            out.write(content);
            out.closeEntry();
        }

        // thread-2: swaps subdir for a symlink while thread-1 is between its ancestor check and its write
        FilePath.afterInitialAncestorCheck = target -> {
            if (target.getName().equals("file1.txt")) {
                Files.createSymbolicLink(new File(base, "subdir").toPath(), outside.toPath());
            }
        };

        // thread-1: extracts subdir/file1.txt
        assertAll(
                () -> {
                    var e = assertThrows(IOException.class, this::untar);
                    assertThat(e.getCause().getMessage(), allOf(
                            containsString("Tar crafted.tar"),
                            containsString("attempts to write to file with symlink in path"),
                            containsString("(during write)"),
                            containsString("subdir/file1.txt")));
                },
                () -> assertThat("file1.txt must not be written through the symlink",
                        new File(outside, "file1.txt").exists(), is(false)));
    }

    @Test
    void symlinkEntryCannotReplaceExtractedDirectory() throws Exception {
        try (var out = new TarOutputStream(Files.newOutputStream(tar.toPath()))) {
            // the subdir/ entry comes first in the same tar; in the real exploit thread-1's mkdirs creates subdir
            out.putNextEntry(new TarEntry("subdir/", true));
            out.closeEntry();
            // thread-2: tries to replace subdir with a symlink to outside
            var link = new TarEntry("subdir", true);
            link.setLinkFlag(TarConstants.LF_SYMLINK);
            link.setLinkName("../outside");
            out.putNextEntry(link);
            out.closeEntry();
        }

        assertAll(
                () -> {
                    var e = assertThrows(IOException.class, this::untar);
                    assertThat(e.getCause(), instanceOf(FileAlreadyExistsException.class));
                },
                () -> assertThat("subdir must stay a real directory",
                        Files.isDirectory(new File(base, "subdir").toPath(), LinkOption.NOFOLLOW_LINKS), is(true)));
    }

    private void untar() throws IOException, InterruptedException {
        new FilePath(tar).untar(new FilePath(base), FilePath.TarCompression.NONE);
    }
}
