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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.FilePath;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author Christoph Thelen
 */
class DirScannerTest {

    @TempDir
    private File tmpRule;

    @Test
    void globShouldUseDefaultExcludes() throws Exception {
        FilePath tmp = new FilePath(tmpRule);
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

    @Test
    void globShouldIgnoreDefaultExcludesByRequest() throws Exception {
        FilePath tmp = new FilePath(tmpRule);
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

    private static class MatchingFileVisitor extends FileVisitor {

        public boolean found = false;

        public final String filename;

        MatchingFileVisitor(String filename) {
            this.filename = filename;
        }

        @Override
        public void visit(File f, String relativePath) {
            if (relativePath.endsWith(filename)) {
                found = true;
            }
        }
    }
}
