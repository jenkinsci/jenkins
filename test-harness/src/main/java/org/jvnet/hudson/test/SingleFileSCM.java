/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe
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
package org.jvnet.hudson.test;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import org.apache.commons.io.IOUtils;

/**
 * {@link SCM} useful for testing that puts just one file in the workspace.
 *
 * @author Kohsuke Kawaguchi
 */
public class SingleFileSCM extends NullSCM {
    private final String path;
    private final byte[] contents;

    public SingleFileSCM(String path, byte[] contents) {
        this.path = path;
        this.contents = contents;
    }

    public SingleFileSCM(String path, String contents) throws UnsupportedEncodingException {
        this.path = path;
        this.contents = contents.getBytes("UTF-8");
    }

    /**
     * When a check out is requested, serve the contents of the URL and place it with the given path name. 
     */
    public SingleFileSCM(String path, URL resource) throws IOException {
        this.path = path;
        this.contents = IOUtils.toByteArray(resource.openStream());
    }

    @Override
    public void checkout(Run<?,?> build, Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
        listener.getLogger().println("Staging "+path);
        OutputStream os = workspace.child(path).write();
        IOUtils.write(contents, os);
        os.close();
    }

    /**
     * Don't write 'this', so that subtypes can be implemented as anonymous class.
     */
    private Object writeReplace() { return new Object(); }
}
