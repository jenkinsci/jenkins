package hudson.tasks.junit;

import java.io.IOException;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;

public abstract class TestDataPublisher implements
		Describable<TestDataPublisher>, ExtensionPoint {

	public abstract TestResultAction.Data getTestData(
			AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener, TestResult testResult) throws IOException, InterruptedException;

	@SuppressWarnings("unchecked")
	public Descriptor<TestDataPublisher> getDescriptor() {
		return Hudson.getInstance().getDescriptor(getClass());
	}

	public static DescriptorExtensionList<TestDataPublisher, Descriptor<TestDataPublisher>> all() {
		return Hudson.getInstance().getDescriptorList(
				TestDataPublisher.class);
	}

}
