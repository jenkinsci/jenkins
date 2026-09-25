/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.FilePath;
import hudson.slaves.DumbSlave;
import hudson.util.DirScanner;
import java.io.OutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

// TODO What is this even testing?
@WithJenkins
class FilePathSecureTest {

    private DumbSlave s;
    private FilePath root, remote;

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        r = rule;
        s = r.createOnlineSlave();
        root = r.jenkins.getRootPath();
        remote = s.getRootPath();
        // to see the difference: DefaultFilePathFilter.BYPASS = true;
    }

    @Test
    void unzip() throws Exception {
        FilePath dir = root.child("dir");
        dir.mkdirs();
        dir.child("stuff").write("hello", null);
        FilePath zip = root.child("dir.zip");
        dir.zip(zip);
        zip.unzip(remote);
        assertEquals("hello", remote.child("dir/stuff").readToString());
    }

    @Test
    void untar() throws Exception {
        FilePath dir = root.child("dir");
        dir.mkdirs();
        dir.child("stuff").write("hello", null);
        FilePath tar = root.child("dir.tar");
        try (OutputStream os = tar.write()) {
            dir.tar(os, new DirScanner.Full());
        }
        tar.untar(remote, FilePath.TarCompression.NONE);
        assertEquals("hello", remote.child("dir/stuff").readToString());
    }

    @Test
    void zip() throws Exception {
        FilePath dir = remote.child("dir");
        dir.mkdirs();
        dir.child("stuff").write("hello", null);
        FilePath zip = root.child("dir.zip");
        dir.zip(zip);
        zip.unzip(root);
        assertEquals("hello", remote.child("dir/stuff").readToString());
    }

    @Test
    void tar() throws Exception {
        FilePath dir = remote.child("dir");
        dir.mkdirs();
        dir.child("stuff").write("hello", null);
        FilePath tar = root.child("dir.tar");
        try (OutputStream os = tar.write()) {
            dir.tar(os, new DirScanner.Full());
        }
        tar.untar(root, FilePath.TarCompression.NONE);
        assertEquals("hello", remote.child("dir/stuff").readToString());
    }

}
