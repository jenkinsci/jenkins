package hudson.model.utils;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Wrote build status to file
 * @author Kanstantsin Shautsou
 */
public class ResultWriterPublisher extends Recorder {
    private final String fileName;

    public ResultWriterPublisher(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        FilePath file = build.getWorkspace().child(fileName);
        file.write(build.getResult().toString(), Charset.defaultCharset().name());
        return true;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) { return true; }
    }
}
