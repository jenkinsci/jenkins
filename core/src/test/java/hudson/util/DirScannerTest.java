/*
 * The MIT License
 * 
 * Copyright (c) 2011, Christoph Thelen
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
package hudson.util;

import hudson.FilePath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Christoph Thelen
 */
public class DirScannerTest {

    @Rule public TemporaryFolder tmpRule = new TemporaryFolder();

    @Test public void globShouldUseDefaultExcludes() throws Exception {
        FilePath tmp = new FilePath(tmpRule.getRoot());
        try {
            tmp.child(".gitignore").touch(0);
            FilePath git = tmp.child(".git");
            git.mkdirs();
            git.child("HEAD").touch(0);
            
            DirScanner glob1 = new DirScanner.Glob("**/*", null);
            DirScanner glob2 = new DirScanner.Glob("**/*", null, true);
            MatchingFileVisitor gitdir = new MatchingFileVisitor("HEAD");
            MatchingFileVisitor gitignore = new MatchingFileVisitor(".gitignore");
            
            glob1.scan(new File(tmp.getRemote()), gitdir);
            glob2.scan(new File(tmp.getRemote()), gitignore);
            
            assertFalse(gitdir.found);
            assertFalse(gitignore.found);
        } finally {
            tmp.deleteRecursive();
        }
    }
    
    @Test public void globShouldIgnoreDefaultExcludesByRequest() throws Exception {
        FilePath tmp = new FilePath(tmpRule.getRoot());
        try {
            tmp.child(".gitignore").touch(0);
            FilePath git = tmp.child(".git");
            git.mkdirs();
            git.child("HEAD").touch(0);
            
            DirScanner glob = new DirScanner.Glob("**/*", null, false);
            MatchingFileVisitor gitdir = new MatchingFileVisitor("HEAD");
            MatchingFileVisitor gitignore = new MatchingFileVisitor(".gitignore");
            
            glob.scan(new File(tmp.getRemote()), gitdir);
            glob.scan(new File(tmp.getRemote()), gitignore);
            
            assertTrue(gitdir.found);
            assertTrue(gitignore.found);
        } finally {
            tmp.deleteRecursive();
        }
    }

    @Test
    @Issue("JENKINS-29780")
    public void testFlattenGlob() throws Exception {
        FilePath tmp = new FilePath(tmpRule.getRoot());
        try {
            FilePath d1 = tmp.child("firstdir");
            d1.mkdirs();
            d1.child("firstfile").touch(0);

            FilePath d2 = tmp.child("seconddir");
            d2.mkdirs();
            d2.child("secondfile").touch(0);

            DirScanner flattenGlob = new DirScanner.FlattenGlob("**/*", null, true);
            FileNameMatchFileVisitor firstFile = new FileNameMatchFileVisitor("firstfile");
            FileNameMatchFileVisitor secondFile = new FileNameMatchFileVisitor("secondfile");

            flattenGlob.scan(new File(tmp.getRemote()), firstFile);
            flattenGlob.scan(new File(tmp.getRemote()), secondFile);

            assertTrue(firstFile.found);
            assertTrue(secondFile.found);
        } finally {
            tmp.deleteRecursive();
        }
    }


    private static class MatchingFileVisitor extends FileVisitor {
    
        public boolean found = false;
        
        public final String filename;
        
        public MatchingFileVisitor(String filename) {
            this.filename = filename;
        }
    
        public void visit(File f, String relativePath) throws IOException {
            if (relativePath.endsWith(filename)) {
                found = true;
            }
        }
    }

    private static class FileNameMatchFileVisitor extends FileVisitor {

        public boolean found = false;

        public final String filename;

        public FileNameMatchFileVisitor(String filename) {
            this.filename = filename;
        }

        public void visit(File f, String relativePath) throws IOException {
            if (relativePath.equals(filename)) {
                found = true;
            }
        }
    }
}
