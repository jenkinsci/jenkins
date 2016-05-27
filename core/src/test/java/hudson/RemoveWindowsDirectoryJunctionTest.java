/*
 *
 */
package hudson;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

public class RemoveWindowsDirectoryJunctionTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void windowsOnly() {
       assumeTrue(Functions.isWindows());
    }

    @Test
    @Issue("JENKINS-2995")
    public void testJunctionIsRemovedButNotContents() throws Exception {
        File subdir1 = tmp.newFolder("notJunction");
        File f1 = new File(subdir1, "testfile1.txt");
        assertTrue("Unable to create temporary file in notJunction directory", f1.createNewFile());
        File j1 = makeJunction(tmp.getRoot(), subdir1);
        Util.deleteRecursive(j1);
        assertFalse("Windows Junction should have been removed", j1.exists());
        assertTrue("Contents of Windows Junction should not be removed", f1.exists());
    }

    private File makeJunction(File baseDir, File pointToDir) throws Exception {
       File junc = new File(baseDir, "test Junction");
       String cmd = "mklink /J \"" + junc.getPath() + "\" \"" + pointToDir.getPath() + "\"";
       ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/C", cmd);
       pb.inheritIO();
       Process p = pb.start();
       assertEquals("Running mklink failed (cmd=" + cmd + ")", 0, p.waitFor());
       return junc;
    }
}
