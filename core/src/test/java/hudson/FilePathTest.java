package hudson;

import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class FilePathTest extends /*RmiTestBase*/ TestCase {
    public void testDummy() {
        // TODO: figure out how to reuse test code from the remoting module.
        // currently it fails because the remoting.jar is signed but
        // test code is not.
    }

    ///**
    // * Copy zero files.
    // */
    //public void testEmptyCopy() throws Exception {
    //    File src = Util.createTempDir();
    //    File dst = Util.createTempDir();
    //    src.deleteOnExit();
    //    dst.deleteOnExit();
    //
    //    new FilePath(src).copyRecursiveTo("**/*",new FilePath(channel,dst.getPath()));
    //}
}
