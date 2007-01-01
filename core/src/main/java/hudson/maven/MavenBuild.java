package hudson.maven;

import hudson.model.AbstractBuild;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenBuild extends AbstractBuild<MavenJob,MavenBuild> {
    public MavenBuild(MavenJob job) throws IOException {
        super(job);
    }

    public MavenBuild(MavenJob job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MavenBuild(MavenJob project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public void run() {
        // TODO
        throw new UnsupportedOperationException();
    }
}
