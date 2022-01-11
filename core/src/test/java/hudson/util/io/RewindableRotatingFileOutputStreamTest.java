package hudson.util.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;

import hudson.FilePath;
import hudson.Functions;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

public class RewindableRotatingFileOutputStreamTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void rotation() throws IOException, InterruptedException {
        File base = tmp.newFile("test.log");
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
    public void deletedFolder() throws Exception {
        assumeFalse("Windows does not allow deleting a directory with a "
            + "file open, so this case should never occur", Functions.isWindows());
        File dir = tmp.newFolder("dir");
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

}
