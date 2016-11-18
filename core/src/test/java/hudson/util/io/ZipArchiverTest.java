package hudson.util.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class ZipArchiverTest {

    private static final Logger LOGGER = Logger.getLogger(ZipArchiverTest.class.getName());

    private File tmpDir;

    @Before
    public void setUp() {
        try {
            // initialize temp directory
            tmpDir = File.createTempFile("temp", ".dir");
            tmpDir.delete();
            tmpDir.mkdir();
        } catch (IOException e) {
            fail("unable to create temp directory", e);
        }
    }

    @After
    public void tearDown() {
        deleteDir(tmpDir);
    }

    @Issue("JENKINS-9942")
    @Test
    public void backwardsSlashesOnWindows()  {
        // create foo/bar/baz/Test.txt
        File tmpFile = null;
        try {
            File baz = new File(new File(new File(tmpDir, "foo"), "bar"), "baz");
            baz.mkdirs();

            tmpFile = new File(baz, "Test.txt");
            tmpFile.createNewFile();
        } catch (IOException e) {
            fail("unable to prepare source directory for zipping", e);
        }

        // a file to store the zip archive in
        File zipFile = null;

        // create zip from tmpDir
        ZipArchiver archiver = null;

        try {
            zipFile = File.createTempFile("test", ".zip");
            archiver = new ZipArchiver(new FileOutputStream(zipFile));

            archiver.visit(tmpFile, "foo\\bar\\baz\\Test.txt");
        } catch (Exception e) {
            fail("exception driving ZipArchiver", e);
        } finally {
            if (archiver != null) {
                try {
                    archiver.close();
                } catch (IOException e) {
                    // ignored
                }
            }
        }

        // examine zip contents and assert that none of the entry names (paths) have
        // back-slashes ("\")
        String zipEntryName = null;

        ZipFile zipFileVerify = null;
        try {
            zipFileVerify = new ZipFile(zipFile);

            zipEntryName = ((ZipArchiveEntry) zipFileVerify.getEntries().nextElement()).getName();
        } catch (Exception e) {
            fail("failure enumerating zip entries", e);
        } finally {
            if (zipFileVerify != null) {
                try {
                    zipFileVerify.close();
                } catch (IOException e) {
                    // ignored
                }
            }
        }

        assertEquals("foo/bar/baz/Test.txt", zipEntryName);
    }

    /**
     * Convenience method for failing with a cause.
     *
     * @param msg the failure description
     * @param cause the root cause of the failure
     */
    private final void fail(final String msg, final Throwable cause) {
        LOGGER.log(Level.SEVERE, msg, cause);
        Assert.fail(msg);
    }

    /**
     * Convenience method for creating a symlink.
     *
     * @param src The symlink source (gets created).
     * @param target The link target (may or may not exist).
     */
    private final void symlink(final File src, final File target) throws IOException {
        Files.createSymbolicLink(src.toPath(), target.toPath());
    }

    @Issue("JENKINS-26700")
    @Test
    public void symbolicLinks() {
        try {
            // First the "regular" stuff
            File foo = new File(tmpDir, "foo");
            File bar = new File(foo, "bar");
            bar.mkdirs();
            File baz = new File(bar, "baz.txt");
            baz.createNewFile();

            // Then the "evil" stuff
            symlink(new File(bar, "baz.linkToNowhere"), new File("nonexistent"));
            symlink(new File(bar, "baz.linkToParentDir"), new File(bar, ".."));
            symlink(new File(foo, "bar.linkToExistingDir"), new File(foo, "bar"));
            symlink(new File(foo, "baz.linkToExistingFile"), new File(bar, "baz.txt"));
        } catch (IOException e) {
            fail("unable to prepare source directory for zipping", e);
        }

        // a file to store the zip archive in
        File zipFile = null;
        // create zip from tmpDir
        ZipArchiver archiver = null;

        try {
            zipFile = File.createTempFile("test2", ".zip");
            archiver = new ZipArchiver(new FileOutputStream(zipFile));

            archiver.visitSymlink(new File(tmpDir, "foo/bar/baz.linkToNowhere"), "nonexistent", "foo/bar/baz.linkToNowhere");
            archiver.visitSymlink(new File(tmpDir, "foo/bar/baz.linkToParentDir"), "..", "foo/bar/baz.linkToParentDir");
            archiver.visitSymlink(new File(tmpDir, "foo/bar.linkToExistingDir"), "bar", "foo/bar.linkToExistingDir");
            archiver.visitSymlink(new File(tmpDir, "foo/baz.linkToExistingFile"), "bar/baz.txt", "foo/baz.linkToExistingFile");
            archiver.visit(new File(tmpDir, "foo/bar/baz.txt"), "foo/bar/baz.txt");
        } catch (Exception e) {
            fail("exception driving ZipArchiver", e);
        } finally {
            if (archiver != null) {
                try {
                    archiver.close();
                } catch (IOException e) {
                    // ignored
                }
            }
        }

        ZipFile zipFileVerify = null;
        try {
            zipFileVerify = new ZipFile(zipFile);
            Enumeration<ZipArchiveEntry> entries = zipFileVerify.getEntries();
            ZipArchiveEntry zae = entries.nextElement();
            assertTrue("Zip entry is a symlink", zae.isUnixSymlink());
            zae = entries.nextElement();
            assertTrue("Zip entry is a symlink", zae.isUnixSymlink());
            zae = entries.nextElement();
            assertTrue("Zip entry is a symlink", zae.isUnixSymlink());
            zae = entries.nextElement();
            assertTrue("Zip entry is a symlink", zae.isUnixSymlink());
            zae = entries.nextElement();
            assertTrue("Zip entry is not a symlink", !zae.isUnixSymlink());
        } catch (Exception e) {
            fail("failure enumerating zip entries", e);
        } finally {
            if (zipFileVerify != null) {
                try {
                    zipFileVerify.close();
                } catch (IOException e) {
                    // ignored
                }
            }
        }
    }

    /**
     * Recursively deletes a directory and all of its children.
     *
     * @param f the File (neé, directory) to delete
     */
    private final void deleteDir(final File f) {
        for (File c : f.listFiles()) {
            if (Files.isDirectory(c.toPath(), NOFOLLOW_LINKS)) {
                deleteDir(c);
            } else {
                c.delete();
            }
        }

        f.delete();
    }
}
