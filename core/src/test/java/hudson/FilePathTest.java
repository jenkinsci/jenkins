/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
