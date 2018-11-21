/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Erik Ramfelt, Richard Bair, id:cactusman
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

import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Kohsuke Kawaguchi
 */
//TODO merge into UtilTest after the security release
public class UtilSEC904Test {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void resolveSymlinkToFile() throws Exception {
        //  root
        //      /a
        //          /aa
        //              aa.txt
        //          /_b => symlink to /root/b
        //      /b
        //          /_a => symlink to /root/a
        File root = tmp.getRoot();
        File a = new File(root, "a");
        File aa = new File(a, "aa");
        aa.mkdirs();
        File aaTxt = new File(aa, "aa.txt");
        FileUtils.write(aaTxt, "aa");
    
        File b = new File(root, "b");
        b.mkdir();
    
        File _a = new File(b, "_a");
        Util.createSymlink(_a.getParentFile(), a.getAbsolutePath(), _a.getName(), TaskListener.NULL);
        
        File _b = new File(a, "_b");
        Util.createSymlink(_b.getParentFile(), b.getAbsolutePath(), _b.getName(), TaskListener.NULL);
        
        assertTrue(Files.isSymbolicLink(_a.toPath()));
        assertTrue(Files.isSymbolicLink(_b.toPath()));
        
        // direct symlinks are resolved
        assertEquals(Util.resolveSymlinkToFile(_a), a);
        assertEquals(Util.resolveSymlinkToFile(_b), b);
        
        // intermediate symlinks are NOT resolved
        assertNull(Util.resolveSymlinkToFile(new File(_a, "aa")));
        assertNull(Util.resolveSymlinkToFile(new File(_a, "aa/aa.txt")));
    }
}
