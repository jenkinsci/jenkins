/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt,
 * Tom Huybrechts, Yahoo!, Inc., Richard Hierlmeier
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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.junit.TestResultAction.Data;
import hudson.tasks.test.TestResultProjectAction;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Generates HTML report from JUnit test result XML files.
 * 
 * @author Kohsuke Kawaguchi
 */
public class JUnitResultArchiver extends Recorder {

    /**
     * {@link FileSet} "includes" string, like "foo/bar/*.xml"
     */
    private final String testResults;

    /**
     * If true, retain a suite's complete stdout/stderr even if this is huge and the suite passed.
     * @since 1.358
     */
    private final boolean keepLongStdio;

    /**
     * {@link TestDataPublisher}s configured for this archiver, to process the recorded data.
     * For compatibility reasons, can be null.
     * @since 1.320
     */
    private final DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers;

    private final Double healthScaleFactor;

	/**
	 * left for backwards compatibility
         * @deprecated since 2009-08-09.
	 */
	@Deprecated
	public JUnitResultArchiver(String testResults) {
		this(testResults, false, null);
	}

    @Deprecated
    public JUnitResultArchiver(String testResults,
            DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers) {
        this(testResults, false, testDataPublishers);
    }
	
	@Deprecated
	public JUnitResultArchiver(
			String testResults,
            boolean keepLongStdio,
			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers) {
        this(testResults, keepLongStdio, testDataPublishers, 1.0);
    }

	@DataBoundConstructor
	public JUnitResultArchiver(
			String testResults,
            boolean keepLongStdio,
			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers,
            double healthScaleFactor) {
		this.testResults = testResults;
        this.keepLongStdio = keepLongStdio;
		this.testDataPublishers = testDataPublishers;
        this.healthScaleFactor = Math.max(0.0,healthScaleFactor);
	}

    /**
     * In progress. Working on delegating the actual parsing to the JUnitParser.
     */
    protected TestResult parse(String expandedTestResults, AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException
    {
        return new JUnitParser(isKeepLongStdio()).parse(expandedTestResults, build, launcher, listener);
    }

    @Override
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {
		listener.getLogger().println(Messages.JUnitResultArchiver_Recording());
		TestResultAction action;
		
		final String testResults = build.getEnvironment(listener).expand(this.testResults);

		try {
			TestResult result = parse(testResults, build, launcher, listener);

			try {
                // TODO can the build argument be omitted now, or is it used prior to the call to addAction?
				action = new TestResultAction(build, result, listener);
			} catch (NullPointerException npe) {
				throw new AbortException(Messages.JUnitResultArchiver_BadXML(testResults));
			}
            action.setHealthScaleFactor(getHealthScaleFactor()); // TODO do we want to move this to the constructor?
            result.freeze(action);
			if (result.isEmpty()) {
			    // most likely a configuration error in the job - e.g. false pattern to match the JUnit result files
				throw new AbortException(Messages.JUnitResultArchiver_ResultIsEmpty());
			}

            // TODO: Move into JUnitParser [BUG 3123310]
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

		build.addAction(action);

		if (action.getResult().getFailCount() > 0)
			build.setResult(Result.UNSTABLE);

		return true;
	}

	/**
	 * Not actually used, but left for backward compatibility
	 * 
	 * @deprecated since 2009-08-10.
	 */
	protected TestResult parseResult(DirectoryScanner ds, long buildTime)
			throws IOException {
		return new TestResult(buildTime, ds);
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public String getTestResults() {
		return testResults;
	}

    public double getHealthScaleFactor() {
        return healthScaleFactor == null ? 1.0 : healthScaleFactor;
    }

    public DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> getTestDataPublishers() {
		return testDataPublishers;
	}

	@Override
	public Collection<Action> getProjectActions(AbstractProject<?, ?> project) {
		return Collections.<Action>singleton(new TestResultProjectAction(project));
	}

	/**
	 * @return the keepLongStdio
	 */
	public boolean isKeepLongStdio() {
		return keepLongStdio;
	}

	private static final long serialVersionUID = 1L;

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		public String getDisplayName() {
			return Messages.JUnitResultArchiver_DisplayName();
		}

        @Override
        public String getHelpFile() {
            return "/help/tasks/junit/report.html";
        }

		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData)
				throws hudson.model.Descriptor.FormException {
			String testResults = formData.getString("testResults");
            boolean keepLongStdio = formData.getBoolean("keepLongStdio");
			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers = new DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>>(Saveable.NOOP);
            try {
                testDataPublishers.rebuild(req, formData, TestDataPublisher.all());
            } catch (IOException e) {
                throw new FormException(e,null);
            }

            return new JUnitResultArchiver(testResults, keepLongStdio, testDataPublishers);
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

        public FormValidation doCheckHealthScaleFactor(@QueryParameter double value) {
            if (value < 1e-7) return FormValidation.warning("Test health reporting disabled");
            return FormValidation.ok(Messages.JUnitResultArchiver_HealthScaleFactorAnalysis(
                    1,
                    (int) (100.0 - Math.max(0.0, Math.min(100.0, 1 * value))),
                    5,
                    (int) (100.0 - Math.max(0.0, Math.min(100.0, 5 * value)))
            ));
        }
    }
}
