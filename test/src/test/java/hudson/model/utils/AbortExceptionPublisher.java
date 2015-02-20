package hudson.model.utils;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

import java.io.IOException;

/**
 * Publisher that throws AbortException
 */
public class AbortExceptionPublisher extends Recorder {
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        throw new AbortException("Throwed AbortException from publisher!");
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() { return BuildStepMonitor.NONE; }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() { return "ThrowAbortExceptionRecorder"; }
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) { return true; }
    }
}
