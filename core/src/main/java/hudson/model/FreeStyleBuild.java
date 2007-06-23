package hudson.model;

import java.io.IOException;
import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class FreeStyleBuild extends Build<FreeStyleProject,FreeStyleBuild> {
    public FreeStyleBuild(FreeStyleProject project) throws IOException {
        super(project);
    }

    public FreeStyleBuild(FreeStyleProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }
}
