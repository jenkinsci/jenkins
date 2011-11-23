package hudson.maven;

/*
 * The MIT License
 * 
 * Copyright (c) 2011, Dominik Bartholdi
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
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.sf.json.JSONObject;

import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Dominik Bartholdi (imod)
 */
public class MavenArgumentInterceptorTest extends HudsonTestCase {

	public void testSimpleMaven3BuildWithArgInterceptor_Goals() throws Exception {

		MavenModuleSet m = createMavenProject();
		MavenInstallation mavenInstallation = configureMaven3();
		m.setMaven(mavenInstallation.getName());
		m.setScm(new ExtractResourceSCM(getClass().getResource("maven3-project.zip")));
		m.setGoals("dummygoal"); // build would fail with this goal

		// add an action to build, redefining the goals and options to be
		// executed
		m.getBuildWrappersList().add(new TestMvnBuildWrapper("clean"));

		MavenModuleSetBuild b = buildAndAssertSuccess(m);
		assertTrue(MavenUtil.maven3orLater(b.getMavenVersionUsed()));
	}

	public void testSimpleMaven3BuildWithArgInterceptor_ArgBuilder() throws Exception {

		MavenModuleSet m = createMavenProject();
		MavenInstallation mavenInstallation = configureMaven3();
		m.setMaven(mavenInstallation.getName());
		m.setScm(new ExtractResourceSCM(getClass().getResource("maven-multimodule-unit-failure.zip")));
		m.setGoals("clean install"); // build would fail because of failing unit
										// tests

		// add an action to build, adding argument to skip the test execution
		m.getBuildWrappersList().add(new TestMvnBuildWrapper(Arrays.asList("-DskipTests")));

		MavenModuleSetBuild b = buildAndAssertSuccess(m);
		assertTrue(MavenUtil.maven3orLater(b.getMavenVersionUsed()));
	}

	private static class TestMvnArgInterceptor implements MavenArgumentInterceptorAction {
		private String goalsAndOptions;
		private List<String> args;

		public TestMvnArgInterceptor(String goalsAndOptions) {
			this.goalsAndOptions = goalsAndOptions;
		}

		public TestMvnArgInterceptor(List<String> args) {
			this.args = args;
		}

		public String getIconFileName() {
			return null;
		}

		public String getDisplayName() {
			return null;
		}

		public String getUrlName() {
			return null;
		}

		public String getGoalsAndOptions(MavenModuleSetBuild build) {
			return goalsAndOptions;
		}

		public ArgumentListBuilder intercept(ArgumentListBuilder mavenargs, MavenModuleSetBuild build) {
			if (args != null) {
				for (String arg : this.args) {
					mavenargs.add(arg);
				}
			}
			return mavenargs;
		}
	}

	public static class TestMvnBuildWrapper extends BuildWrapper {
		public Result buildResultInTearDown;
		private String goalsAndOptions;
		private List<String> args;

		public TestMvnBuildWrapper(String goalsAndOptions) {
			this.goalsAndOptions = goalsAndOptions;
		}

		public TestMvnBuildWrapper(List<String> args) {
			this.args = args;
		}

		@Override
		public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

			if (goalsAndOptions != null) {
				build.addAction(new TestMvnArgInterceptor(goalsAndOptions));
			} else if (args != null) {
				build.addAction(new TestMvnArgInterceptor(args));
			}

			return new BuildWrapper.Environment() {
				@Override
				public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
					buildResultInTearDown = build.getResult();
					return true;
				}
			};
		}

		@Extension
		public static class TestMvnBuildWrapperDescriptor extends BuildWrapperDescriptor {
			@Override
			public boolean isApplicable(AbstractProject<?, ?> project) {
				return true;
			}

			@Override
			public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) {
				throw new UnsupportedOperationException();
			}

			@Override
			public String getDisplayName() {
				return this.getClass().getName();
			}
		}
	}
}
