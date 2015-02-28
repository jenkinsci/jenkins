/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

package jenkins.util;

import hudson.Util;
import hudson.model.TaskListener;
import java.io.File;
import java.io.FileNotFoundException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

public class VirtualFileTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    
    @Issue("SECURITY-162")
    @Test public void outsideSymlinks() throws Exception {
        File ws = tmp.newFolder("ws");
        FileUtils.write(new File(ws, "safe"), "safe");
        Util.createSymlink(ws, "safe", "supported", TaskListener.NULL);
        File other = tmp.newFolder("other");
        FileUtils.write(new File(other, "secret"), "s3cr3t");
        Util.createSymlink(ws, "../other/secret", "hack", TaskListener.NULL);
        VirtualFile root = VirtualFile.forFile(ws);
        VirtualFile supported = root.child("supported");
        assertTrue(supported.isFile());
        assertTrue(supported.exists());
        assertEquals("safe", IOUtils.toString(supported.open(), (String) null));
        VirtualFile hack = root.child("hack");
        assertFalse(hack.isFile());
        assertFalse(hack.exists());
        try {
            hack.open();
            fail();
        } catch (FileNotFoundException x) {
            // OK
        }
    }

}