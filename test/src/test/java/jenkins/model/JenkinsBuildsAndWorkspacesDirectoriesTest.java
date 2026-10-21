package jenkins.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Items;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.fixtures.RealJenkinsFixture;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

/**
 * Since JENKINS-50164, Jenkins#workspacesDir and Jenkins#buildsDir had their associated UI deleted.
 * So instead of configuring through the UI, we now have to use sysprops for this.
 */
class JenkinsBuildsAndWorkspacesDirectoriesTest {

    @RegisterExtension
    private final RealJenkinsExtension story = new RealJenkinsExtension();

    @TempDir
    private File tmp;

    @Issue("JENKINS-50164")
    @Test
    void badValueForBuildsDir() throws Throwable {
        story.then(rule -> {
            final List<String> badValues = new ArrayList<>(Arrays.asList(
                    "blah",
                    "$JENKINS_HOME",
                    "$JENKINS_HOME/builds",
                    "$ITEM_FULL_NAME",
                    "/path/to/builds",
                    "/invalid/$JENKINS_HOME",
                    "relative/ITEM_FULL_NAME"));
            if (!new File("/").canWrite()) {
                badValues.add("/foo/$ITEM_FULL_NAME");
                badValues.add("/$ITEM_FULLNAME");
            } // else perhaps running as root

            for (String badValue : badValues) {
                assertThrows(InvalidBuildsDir.class, () -> Jenkins.checkRawBuildsDir(badValue), badValue + " should have been rejected");
            }
        });
    }

    @Issue("JENKINS-50164")
    @Test
    void goodValueForBuildsDir() throws Throwable {
        story.then(rule -> {
            final List<String> badValues = Arrays.asList(
                    "$JENKINS_HOME/foo/$ITEM_FULL_NAME",
                    "${ITEM_ROOTDIR}/builds");

            for (String goodValue : badValues) {
                Jenkins.checkRawBuildsDir(goodValue);
            }
        });
    }

    private void setRawBuildsDir(String rawBuildsDir) {
        story.javaOptions("-D" + Jenkins.BUILDS_DIR_PROP + "=" + rawBuildsDir);
    }

    @Issue("JENKINS-50164")
    @Test
    void jenkinsDoesNotStartWithBadSysProp() throws Throwable {
        setRawBuildsDir("/bluh");
        assertThrows(RealJenkinsFixture.JenkinsStartupException.class, () -> story.then(step -> fail("should have failed before reaching here.")));
    }

    @Test
    @Issue("JENKINS-17138")
    void externalBuildDirectoryRenameDelete() throws Throwable {
        setRawBuildsDir(tmp + "/${ITEM_FULL_NAME}");
        var _tmp = tmp;
        story.then(steps -> {
            assertEquals(_tmp + "/${ITEM_FULL_NAME}", steps.jenkins.getRawBuildsDir());
            FreeStyleProject p = steps.jenkins.createProject(Folder.class, "d").createProject(FreeStyleProject.class, "prj");
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            File oldBuildDir = new File(_tmp, "d/prj");
            assertEquals(new File(oldBuildDir, b.getId()), b.getRootDir());
            assertTrue(b.getRootDir().isDirectory());
            p.renameTo("proj");
            File newBuildDir = new File(_tmp, "d/proj");
            assertEquals(new File(newBuildDir, b.getId()), b.getRootDir());
            assertTrue(b.getRootDir().isDirectory());
            p.delete();
            assertFalse(b.getRootDir().isDirectory());
        });
    }

    @Issue("JENKINS-24825")
    @Test
    void moveItem() throws Throwable {
        setRawBuildsDir(tmp + "/${ITEM_FULL_NAME}");
        var _tmp = tmp;
        story.then(r -> {
            var foo = r.createProject(Folder.class, "foo");
            var bar = r.createProject(Folder.class, "bar");
            var test = foo.createProject(FreeStyleProject.class, "test");
            r.buildAndAssertSuccess(test);
            Items.move(test, bar);
            assertFalse(new File(_tmp, "foo/test/1").exists());
            assertTrue(new File(_tmp, "bar/test/1").exists());
        });
    }

    @Issue("JENKINS-19764")
    @Test
    void testRenameWithCustomBuildsDirWithSubdir() throws Throwable {
        setRawBuildsDir("${JENKINS_HOME}/builds/${ITEM_FULL_NAME}/builds");
        story.then(j -> {
            final FreeStyleProject p = j.createFreeStyleProject();
            j.buildAndAssertSuccess(p);
            p.renameTo("different-name");
        });
    }

    @Issue("JENKINS-44657")
    @Test
    void testRenameWithCustomBuildsDirWithBuildsIntact() throws Throwable {
        setRawBuildsDir("${JENKINS_HOME}/builds/${ITEM_FULL_NAME}/builds");
        story.then(j -> {
            final FreeStyleProject p = j.createFreeStyleProject();
            final File oldBuildsDir = p.getBuildDir();
            j.buildAndAssertSuccess(p);
            String oldDirContent = dirContent(oldBuildsDir);
            p.renameTo("different-name");
            final File newBuildDir = p.getBuildDir();
            assertNotNull(newBuildDir);
            assertNotEquals(oldBuildsDir.getAbsolutePath(), newBuildDir.getAbsolutePath());
            String newDirContent = dirContent(newBuildDir);
            assertEquals(oldDirContent, newDirContent);
        });
    }

    @Issue("JENKINS-44657")
    @Test
    void testRenameWithCustomBuildsDirWithBuildsIntactInFolder() throws Throwable {
        setRawBuildsDir("${JENKINS_HOME}/builds/${ITEM_FULL_NAME}/builds");
        story.then(j -> {
            var f = j.createProject(Folder.class, "F");

            final FreeStyleProject p1 = f.createProject(FreeStyleProject.class, "P1");
            j.buildAndAssertSuccess(p1);
            File oldP1BuildsDir = p1.getBuildDir();
            final String oldP1DirContent = dirContent(oldP1BuildsDir);
            f.renameTo("different-name");

            File newP1BuildDir = p1.getBuildDir();
            assertNotNull(newP1BuildDir);
            assertNotEquals(oldP1BuildsDir.getAbsolutePath(), newP1BuildDir.getAbsolutePath());
            String newP1DirContent = dirContent(newP1BuildDir);
            assertEquals(oldP1DirContent, newP1DirContent);

            final FreeStyleProject p2 = f.createProject(FreeStyleProject.class, "P2");
            if (Functions.isWindows()) {
                p2.getBuildersList().add(new BatchFile("echo hello > hello.txt"));
            } else {
                p2.getBuildersList().add(new Shell("echo hello > hello.txt"));
            }
            p2.getPublishersList().add(new ArtifactArchiver("*.txt"));
            j.buildAndAssertSuccess(p2);

            File oldP2BuildsDir = p2.getBuildDir();
            final String oldP2DirContent = dirContent(oldP2BuildsDir);
            FreeStyleBuild b2 = p2.getBuilds().getLastBuild();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b2.getLogText().writeRawLogTo(0, out); // TODO use writeWholeLogTo?
            final String oldB2Log = out.toString(Charset.defaultCharset());
            assertTrue(b2.getArtifactManager().root().child("hello.txt").exists());
            f.renameTo("something-else");

            //P1 check again
            newP1BuildDir = p1.getBuildDir();
            assertNotNull(newP1BuildDir);
            assertNotEquals(oldP1BuildsDir.getAbsolutePath(), newP1BuildDir.getAbsolutePath());
            newP1DirContent = dirContent(newP1BuildDir);
            assertEquals(oldP1DirContent, newP1DirContent);

            //P2 check

            b2 = p2.getBuilds().getLastBuild();
            assertNotNull(b2);
            out = new ByteArrayOutputStream();
            b2.getLogText().writeRawLogTo(0, out); // TODO use writeWholeLogTo?
            final String newB2Log = out.toString(Charset.defaultCharset());
            assertEquals(oldB2Log, newB2Log);
            assertTrue(b2.getArtifactManager().root().child("hello.txt").exists());

            File newP2BuildDir = p2.getBuildDir();
            assertNotNull(newP2BuildDir);
            assertNotEquals(oldP2BuildsDir.getAbsolutePath(), newP2BuildDir.getAbsolutePath());
            String newP2DirContent = dirContent(newP2BuildDir);
            assertEquals(oldP2DirContent, newP2DirContent);
        });
    }

    private static String dirContent(File dir) throws IOException, InterruptedException {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        StringBuilder str = new StringBuilder();
        final FilePath[] list = new FilePath(dir).list("**/*");
        Arrays.sort(list, Comparator.comparing(FilePath::getRemote));
        for (FilePath path : list) {
            str.append(relativePath(dir, path));
            str.append(' ').append(path.length()).append('\n');
        }
        return str.toString();
    }

    private static String relativePath(File base, FilePath path) throws IOException, InterruptedException {
        if (path.absolutize().getRemote().equals(base.getAbsolutePath())) {
            return "";
        } else {
            final String s = relativePath(base, path.getParent());
            if (s.isEmpty()) {
                return path.getName();
            } else {
                return s + "/" + path.getName();
            }
        }
    }

}
