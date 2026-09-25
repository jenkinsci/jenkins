/*
 *
 */

package hudson;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.os.WindowsUtil;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;

@For(Util.class)
// https://superuser.com/q/343074
class RemoveWindowsDirectoryJunctionTest {

    @TempDir
    private File tmp;

    @BeforeEach
    void windowsOnly() {
       assumeTrue(Functions.isWindows());
    }

    @Test
    @Issue("JENKINS-2995")
    void testJunctionIsRemovedButNotContents() throws Exception {
        File subdir1 = newFolder(tmp, "notJunction");
        File f1 = new File(subdir1, "testfile1.txt");
        assertTrue(f1.createNewFile(), "Unable to create temporary file in notJunction directory");
        File j1 = WindowsUtil.createJunction(new File(tmp, "test junction"), subdir1);
        Util.deleteRecursive(j1);
        assertFalse(j1.exists(), "Windows Junction should have been removed");
        assertTrue(f1.exists(), "Contents of Windows Junction should not be removed");
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
