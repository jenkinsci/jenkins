package hudson.util.io;

import junit.framework.TestCase;
import org.jvnet.hudson.test.Bug;

import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;


public class ZipArchiverTest extends TestCase {
    private static final Logger LOGGER = Logger.getLogger(ZipArchiverTest.class.getName());
    
    private File tmpDir;
    
    @Override
    protected void setUp() {
        try {
            // initialize temp directory
            tmpDir = File.createTempFile("temp", ".dir");
            tmpDir.delete();
            tmpDir.mkdir();
        } catch (IOException e) {
            fail("unable to create temp directory", e);
        }
    }
    
    @Override
    protected void tearDown() {
        deleteDir(tmpDir);
    }
    
    @Bug(9942)
    public void testBackwardsSlashesOnWindows()  {
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
        
            zipEntryName = ((ZipEntry) zipFileVerify.entries().nextElement()).getName();
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
        fail(msg);
    }
    
    /**
     * Recursively deletes a directory and all of its children.
     *
     * @param f the File (neé, directory) to delete
     */
    private final void deleteDir(final File f) {
        for (File c : f.listFiles()) {
            if (c.isDirectory()) {
                deleteDir(c);
            } else {
                c.delete();
            }
        }
        
        f.delete();
    }
}
