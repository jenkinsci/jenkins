/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * {@link SCM} useful for testing that extracts the given resource as a zip file.
 *
 * @author Kohsuke Kawaguchi
 */
public class ExtractResourceSCM extends NullSCM {
    private final URL zip;

    private String parentFolder;

    /**
     *
     * @param zip
     */
    public ExtractResourceSCM(URL zip) {
        if(zip==null)
            throw new IllegalArgumentException();
        this.zip = zip;
    }

    /**
     * with this constructor your zip can contains a folder
     * more usefull to create a project test zip foo.zip foo
     * @param zip
     * @param parentFolder
     */
    public ExtractResourceSCM(URL zip, String parentFolder) {
        if(zip==null)
            throw new IllegalArgumentException();
        this.zip = zip;
        this.parentFolder = parentFolder;
    }

    @Override
    public boolean checkout(AbstractBuild<?,?> build, Launcher launcher, FilePath workspace, BuildListener listener, File changeLogFile) throws IOException, InterruptedException {
    	if (workspace.exists()) {
            listener.getLogger().println("Deleting existing workspace " + workspace.getRemote());
    		workspace.deleteRecursive();
    	}
        listener.getLogger().println("Staging "+zip);
        workspace.unzipFrom(zip.openStream());
        if (parentFolder != null) {
            FileUtils.copyDirectory( new File(workspace.getRemote() + "/" + parentFolder), new File( workspace.getRemote()));
        }
        return true;
    }
}
