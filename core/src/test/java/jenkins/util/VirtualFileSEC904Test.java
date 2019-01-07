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

import hudson.FilePath;
import hudson.model.TaskListener;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

//TODO merge into VirtualFileTest after the security release
public class VirtualFileSEC904Test {
    
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    //  root
    //      /a
    //          /aa
    //              /aaa
    //                  /_b2 => symlink to /root/b
    //              aa.txt
    //          /ab
    //              ab.txt
    //          /_b => symlink to /root/b
    //      /b
    //          /_a => symlink to /root/a
    //          /ba
    //              ba.txt
    private void prepareFileStructureForIsDescendant() throws Exception {
        File root = tmp.getRoot();
        File a = new File(root, "a");
        File aa = new File(a, "aa");
        File aaa = new File(aa, "aaa");
        aaa.mkdirs();
        File aaTxt = new File(aa, "aa.txt");
        FileUtils.write(aaTxt, "aa");
        
        File ab = new File(a, "ab");
        ab.mkdirs();
        File abTxt = new File(ab, "ab.txt");
        FileUtils.write(abTxt, "ab");
        
        File b = new File(root, "b");
        
        File ba = new File(b, "ba");
        ba.mkdirs();
        File baTxt = new File(ba, "ba.txt");
        FileUtils.write(baTxt, "ba");
        
        File _a = new File(b, "_a");
        new FilePath(_a).symlinkTo(a.getAbsolutePath(), TaskListener.NULL);
        
        File _b = new File(a, "_b");
        new FilePath(_b).symlinkTo(b.getAbsolutePath(), TaskListener.NULL);
        File _b2 = new File(aaa, "_b2");
        new FilePath(_b2).symlinkTo(b.getAbsolutePath(), TaskListener.NULL);
    }
    
    @Issue("SECURITY-904")
    @Test public void forFile_isDescendant() throws Exception {
        this.prepareFileStructureForIsDescendant();
        
        File root = tmp.getRoot();
        File a = new File(root, "a");
        File aa = new File(a, "aa");
        VirtualFile virtualRoot = VirtualFile.forFile(root);
        // keep the root information for isDescendant
        VirtualFile virtualRootChildA = virtualRoot.child("a");
        VirtualFile virtualFromA = VirtualFile.forFile(a);
        
        checkCommonAssertionForIsDescendant(virtualRoot, virtualRootChildA, virtualFromA, aa.getAbsolutePath());
    }
    
    @Test
    @Issue("SECURITY-904")
    public void forFilePath_isDescendant() throws Exception {
        this.prepareFileStructureForIsDescendant();
        
        File root = tmp.getRoot();
        File a = new File(root, "a");
        File aa = new File(a, "aa");
        VirtualFile virtualRoot = VirtualFile.forFilePath(new FilePath(root));
        // keep the root information for isDescendant
        VirtualFile virtualRootChildA = virtualRoot.child("a");
        VirtualFile virtualFromA = VirtualFile.forFilePath(new FilePath(a));
        
        checkCommonAssertionForIsDescendant(virtualRoot, virtualRootChildA, virtualFromA, aa.getAbsolutePath());
    }
    
    private void checkCommonAssertionForIsDescendant(VirtualFile virtualRoot, VirtualFile virtualRootChildA, VirtualFile virtualFromA, String absolutePath) throws Exception {
        try {
            virtualRootChildA.isDescendant(absolutePath);
            fail("isDescendant should have refused the absolute path");
        } catch (IllegalArgumentException e) {}
        
        assertTrue(virtualRootChildA.isDescendant("aa"));
        assertTrue(virtualRootChildA.isDescendant("aa/aa.txt"));
        assertTrue(virtualRootChildA.isDescendant("aa\\aa.txt"));
        assertTrue(virtualRootChildA.isDescendant("ab"));
        assertTrue(virtualRootChildA.isDescendant("ab/ab.txt"));
        assertTrue(virtualRootChildA.isDescendant("ab//ab.txt"));
        assertTrue(virtualRootChildA.isDescendant("ab/nonExistingFile.txt"));
        assertTrue(virtualRootChildA.isDescendant("nonExistingFolder"));
        assertTrue(virtualRootChildA.isDescendant("nonExistingFolder/nonExistingFile.txt"));
        
        assertTrue(virtualRootChildA.isDescendant("_b"));
        assertTrue(virtualRootChildA.isDescendant("_b/ba"));
        assertTrue(virtualRootChildA.isDescendant("_b/ba/ba.txt"));
        assertTrue(virtualRootChildA.isDescendant("aa/aaa/_b2"));
        assertTrue(virtualRootChildA.isDescendant("aa/aaa/_b2/ba"));
        assertTrue(virtualRootChildA.isDescendant("aa/aaa/_b2/ba/ba.txt"));
        
        // such approach could be used to check existence of file inside symlink
        assertTrue(virtualRootChildA.isDescendant("_b/ba/ba-unexistingFile.txt"));
        
        // we go outside, then inside = forbidden, could be used to check existence of symlinks
        assertTrue(virtualRootChildA.isDescendant("_b/_a"));
        assertTrue(virtualRootChildA.isDescendant("_b/_a/aa"));
        assertTrue(virtualRootChildA.isDescendant("_b/_a/aa/aa.txt"));
        
        assertTrue(virtualFromA.isDescendant("aa"));
        assertFalse(virtualFromA.isDescendant("_b"));
        assertFalse(virtualFromA.isDescendant("_b/ba/ba-unexistingFile.txt"));
        assertFalse(virtualFromA.isDescendant("_b/_a"));
        assertFalse(virtualFromA.isDescendant("_b/_a/aa"));
        assertFalse(virtualFromA.isDescendant("_b/_a/aa/aa.txt"));
        assertFalse(virtualFromA.isDescendant("aa/aaa/_b2"));
        assertFalse(virtualFromA.isDescendant("aa/aaa/_b2/ba"));
        assertFalse(virtualFromA.isDescendant("aa/aaa/_b2/ba/ba.txt"));
        
        assertTrue(virtualRoot.isDescendant("aa"));
        assertTrue(virtualRoot.isDescendant("aa/aa.txt"));
        assertTrue(virtualRoot.isDescendant("ab"));
        assertTrue(virtualRoot.isDescendant("ab/ab.txt"));
        assertTrue(virtualRoot.isDescendant("ab/nonExistingFile.txt"));
        assertTrue(virtualRoot.isDescendant("nonExistingFolder"));
        assertTrue(virtualRoot.isDescendant("nonExistingFolder/nonExistingFile.txt"));
        
        assertTrue(virtualRoot.isDescendant("_b"));
        assertTrue(virtualRoot.isDescendant("_b/ba"));
        assertTrue(virtualRoot.isDescendant("_b/ba/ba.txt"));
        assertTrue(virtualRoot.isDescendant("_b/_a"));
        assertTrue(virtualRoot.isDescendant("_b/_a/aa"));
        assertTrue(virtualRoot.isDescendant("_b/_a/aa/"));
        assertTrue(virtualRoot.isDescendant("_b/_a/aa/../ab/ab.txt"));
        assertTrue(virtualRoot.isDescendant("_b/_a/aa/aa.txt"));
    }
}
