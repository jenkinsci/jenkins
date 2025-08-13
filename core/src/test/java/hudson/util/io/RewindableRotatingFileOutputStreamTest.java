package hudson.util.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.FilePath;
import hudson.Functions;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

class RewindableRotatingFileOutputStreamTest {

    @TempDir
    private File tmp;

    @Test
    void rotation() throws IOException, InterruptedException {
        File base = Files.createTempFile(tmp.toPath(), "test.log", null).toFile();
        RewindableRotatingFileOutputStream os = new RewindableRotatingFileOutputStream(base, 3);
        PrintWriter w = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
        for (int i = 0; i <= 4; i++) {
            w.println("Content" + i);
            os.rewind();
        }
        w.println("Content5");
        w.close();

        assertEquals("Content5", new FilePath(base).readToString().trim());
        assertEquals("Content4", new FilePath(new File(base.getPath() + ".1")).readToString().trim());
        assertEquals("Content3", new FilePath(new File(base.getPath() + ".2")).readToString().trim());
        assertEquals("Content2", new FilePath(new File(base.getPath() + ".3")).readToString().trim());
        assertFalse(new File(base.getPath() + ".4").exists());

        os.deleteAll();
    }

    @Issue("JENKINS-16634")
    @Test
    void deletedFolder() throws Exception {
        assumeFalse(Functions.isWindows(), "Windows does not allow deleting a directory with a "
            + "file open, so this case should never occur");
        File dir = newFolder(tmp, "dir");
        File base = new File(dir, "x.log");
        RewindableRotatingFileOutputStream os = new RewindableRotatingFileOutputStream(base, 3);
        for (int i = 0; i < 2; i++) {
            FileUtils.deleteDirectory(dir);
            os.write('.');
            FileUtils.deleteDirectory(dir);
            os.write('.');
            FileUtils.deleteDirectory(dir);
            os.rewind();
        }
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
