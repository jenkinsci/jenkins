package hudson.model;

import hudson.FilePath;
import org.junit.Test;

import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class ComputerTest {
    @Test
    public void testRelocate() throws Exception {
        File d = File.createTempFile("jenkins", "test");
        FilePath dir = new FilePath(d);
        try {
            dir.delete();
            dir.mkdirs();
            dir.child("slave-abc.log").touch(0);
            dir.child("slave-def.log.5").touch(0);

            Computer.relocateOldLogs(d);

            assert dir.list().size()==1; // asserting later that this one child is the logs/ directory
            assert dir.child("logs/slaves/abc/slave.log").exists();
            assert dir.child("logs/slaves/def/slave.log.5").exists();
        } finally {
            dir.deleteRecursive();
        }
    }
}
