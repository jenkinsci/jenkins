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
import java.nio.charset.Charset;

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

    public SingleFileSCM(String path, String contents) {
        this.path = path;
        this.contents = contents.getBytes(Charset.forName("UTF-8"));
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
