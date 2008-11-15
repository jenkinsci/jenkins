package org.jvnet.hudson.test;

import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.Launcher;
import hudson.FilePath;

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
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changeLogFile) throws IOException, InterruptedException {
        listener.getLogger().println("Staging "+path);
        OutputStream os = workspace.child(path).write();
        IOUtils.write(contents, os);
        os.close();
        return true;
    }
}
