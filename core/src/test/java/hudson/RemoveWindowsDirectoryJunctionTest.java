/*
 *
 */

package hudson;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import hudson.os.WindowsUtil;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;

@For(Util.class)
// https://superuser.com/q/343074
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
        File j1 = WindowsUtil.createJunction(new File(tmp.getRoot(), "test junction"), subdir1);
        Util.deleteRecursive(j1);
        assertFalse("Windows Junction should have been removed", j1.exists());
        assertTrue("Contents of Windows Junction should not be removed", f1.exists());
    }

}
