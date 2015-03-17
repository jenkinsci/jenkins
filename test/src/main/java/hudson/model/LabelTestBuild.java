package hudson.model;

import java.io.*;

public class LabelTestBuild extends Build<LabelTestProject,LabelTestBuild> {
    public LabelTestBuild(LabelTestProject project) throws IOException {
        super(project);
    }

    public LabelTestBuild(LabelTestProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public void run() {
        execute(new BuildExecution());
    }
}

