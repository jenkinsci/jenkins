/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt, Tom Huybrechts
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
package hudson.tasks.junit;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.FilePath.FileCallable;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.CheckPoint;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.junit.TestResultAction.Data;
import hudson.tasks.test.TestResultAggregator;
import hudson.tasks.test.TestResultProjectAction;
import hudson.util.DescribableList;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONObject;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Generates HTML report from JUnit test result XML files.
 * 
 * @author Kohsuke Kawaguchi
 */
public class JUnitResultArchiver extends Recorder implements Serializable,
		MatrixAggregatable {

	/**
	 * {@link FileSet} "includes" string, like "foo/bar/*.xml"
	 */
	private final String testResults;

    /**
     * {@link TestDataPublisher}s configured for this archiver, to process the recorded data.
     * For compatibility reasons, can be null.
     * @since 1.320
     */
	private final DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers;

	/**
	 * left for backwards compatibility
	 */
	@Deprecated
	public JUnitResultArchiver(String testResults) {
		this(testResults, null);
	}
	
	@DataBoundConstructor
	public JUnitResultArchiver(
			String testResults,
			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers) {
		this.testResults = testResults;
		this.testDataPublishers = testDataPublishers;
	}

	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {
		listener.getLogger().println(Messages.JUnitResultArchiver_Recording());
		TestResultAction action;
		
		final String testResults = build.getEnvironment(listener).expand(this.testResults);

		try {
			final long buildTime = build.getTimestamp().getTimeInMillis();
			final long nowMaster = System.currentTimeMillis();

			TestResult result = build.getWorkspace().act(
					new ParseResultCallable(testResults, buildTime, nowMaster));

			action = new TestResultAction(build, result, listener);
			if (result.getPassCount() == 0 && result.getFailCount() == 0)
				throw new AbortException(Messages.JUnitResultArchiver_ResultIsEmpty());

			List<Data> data = new ArrayList<Data>();
			if (testDataPublishers != null) {
				for (TestDataPublisher tdp : testDataPublishers) {
					Data d = tdp.getTestData(build, launcher, listener, result);
					if (d != null) {
						data.add(d);
					}
				}
			}

			action.setData(data);

			CHECKPOINT.block();

		} catch (AbortException e) {
			if (build.getResult() == Result.FAILURE)
				// most likely a build failed before it gets to the test phase.
				// don't report confusing error message.
				return true;

			listener.getLogger().println(e.getMessage());
			build.setResult(Result.FAILURE);
			return true;
		} catch (IOException e) {
			e.printStackTrace(listener.error("Failed to archive test reports"));
			build.setResult(Result.FAILURE);
			return true;
		}

		build.getActions().add(action);
		CHECKPOINT.report();

		if (action.getResult().getFailCount() > 0)
			build.setResult(Result.UNSTABLE);

		return true;
	}

	/**
	 * Not actually used, but left for backward compatibility
	 * 
	 * @deprecated
	 */
	protected TestResult parseResult(DirectoryScanner ds, long buildTime)
			throws IOException {
		return new TestResult(buildTime, ds);
	}

	/**
	 * This class does explicit checkpointing.
	 */
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public String getTestResults() {
		return testResults;
	}

	public DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> getTestDataPublishers() {
		return testDataPublishers;
	}

	@Override
	public Action getProjectAction(AbstractProject<?, ?> project) {
		return new TestResultProjectAction(project);
	}

	public MatrixAggregator createAggregator(MatrixBuild build,
			Launcher launcher, BuildListener listener) {
		return new TestResultAggregator(build, launcher, listener);
	}

	/**
	 * Test result tracks the diff from the previous run, hence the checkpoint.
	 */
	private static final CheckPoint CHECKPOINT = new CheckPoint(
			"JUnit result archiving");

	private static final long serialVersionUID = 1L;

	private static final class ParseResultCallable implements
			FileCallable<TestResult> {
		private final long buildTime;
		private final String testResults;
		private final long nowMaster;

		private ParseResultCallable(String testResults, long buildTime, long nowMaster) {
			this.buildTime = buildTime;
			this.testResults = testResults;
			this.nowMaster = nowMaster;
		}

		public TestResult invoke(File ws, VirtualChannel channel) throws IOException {
			final long nowSlave = System.currentTimeMillis();

			FileSet fs = Util.createFileSet(ws, testResults);
			DirectoryScanner ds = fs.getDirectoryScanner();

			String[] files = ds.getIncludedFiles();
			if (files.length == 0) {
				// no test result. Most likely a configuration
				// error or fatal problem
				throw new AbortException(Messages.JUnitResultArchiver_NoTestReportFound());
			}

			return new TestResult(buildTime + (nowSlave - nowMaster), ds);
		}
	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		public String getDisplayName() {
			return Messages.JUnitResultArchiver_DisplayName();
		}

		public String getHelpFile() {
			return "/help/tasks/junit/report.html";
		}

		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData)
				throws hudson.model.Descriptor.FormException {
			String testResults = formData.getString("testResults");
			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers = new DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>>(
					new Saveable() {
						public void save() throws IOException {
							// no-op
						}
					});
			testDataPublishers.rebuild(req, formData, TestDataPublisher.all());

			return new JUnitResultArchiver(testResults, testDataPublishers);
		}

		/**
		 * Performs on-the-fly validation on the file mask wildcard.
		 */
		public FormValidation doCheckTestResults(
				@AncestorInPath AbstractProject project,
				@QueryParameter String value) throws IOException {
			return FilePath.validateFileMask(project.getSomeWorkspace(), value);
		}

		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}
	}
}
