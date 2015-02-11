package hudson.util.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import hudson.FilePath;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Kohsuke Kawaguchi
 */
public class ReopenableRotatingFileOutputStreamTest {

    @Test
    public void rotation() throws IOException, InterruptedException {
        File base = File.createTempFile("test", "log");
        ReopenableRotatingFileOutputStream os = new ReopenableRotatingFileOutputStream(base,3);
        PrintWriter w = new PrintWriter(os,true);
        for (int i=0; i<=4; i++) {
            w.println("Content"+i);
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
}
