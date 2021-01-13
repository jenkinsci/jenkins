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

import hudson.FilePath;
import hudson.slaves.DumbSlave;
import hudson.util.DirScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import hudson.util.io.ArchiverFactory;
import org.junit.Ignore;
import org.junit.Test;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class FilePathSecureTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    private DumbSlave s;
    private FilePath root, remote;

    @Before public void init() throws Exception {
        s = r.createOnlineSlave();
        root = r.jenkins.getRootPath();
        remote = s.getRootPath();
        // to see the difference: DefaultFilePathFilter.BYPASS = true;
    }
    @Issue("JENKINS-40912")
    @Test public void archiving() throws Exception {
        FilePath dir = root.child("dir");
        dir.mkdirs();
        dir.child("stuff").write("hello", null);

        FilePath folder = dir.child("folder");
        folder.mkdirs();
        folder.child("more-stuff").write("hello", null);

        FilePath zip = root.child("dir.zip");

        // consumer to access to the (un)compressed files
        final List<String> files = new ArrayList<>();
        Consumer<String> function = (Consumer<String> & Serializable)(a) -> {
            System.out.println("a "+ a);
            files.add(a);
        };

        OutputStream os = zip.write();
        DirScanner.Glob scanner = new DirScanner.Glob("**", "");
        dir.archive(ArchiverFactory.TAR, os, scanner, function);

        // assert that the former two files are archived.
        assertThat(files.size(), is(2));
        assertThat(files, containsInAnyOrder(
                "stuff",
                "folder/more-stuff"
        ));
        files.clear();


        FilePath out = root.child("output");
        out.mkdirs();
        zip.untar(out, FilePath.TarCompression.NONE, function);

        assertThat(files.size(), is(2));
        assertThat(files, containsInAnyOrder(
                "stuff",
                "folder/more-stuff"
        ));
    }

    @Test public void unzip() throws Exception {
        FilePath dir = root.child("dir");
        dir.mkdirs();
        dir.child("stuff").write("hello", null);
        FilePath zip = root.child("dir.zip");
        dir.zip(zip);
        zip.unzip(remote);
        assertEquals("hello", remote.child("dir/stuff").readToString());
    }

    @Test public void untar() throws Exception {
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

    @Test public void zip() throws Exception {
        FilePath dir = remote.child("dir");
        dir.mkdirs();
        dir.child("stuff").write("hello", null);
        FilePath zip = root.child("dir.zip");
        dir.zip(zip);
        zip.unzip(root);
        assertEquals("hello", remote.child("dir/stuff").readToString());
    }

    @Test public void tar() throws Exception {
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
