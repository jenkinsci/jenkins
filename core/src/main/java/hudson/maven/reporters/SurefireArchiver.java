/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jason Chaffee, Maciek Starzyk
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
package hudson.maven.reporters;

import hudson.Util;
import hudson.Extension;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.maven.MavenBuilder;
import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.TestResultProjectAction;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Records the surefire test result.
 * @author Kohsuke Kawaguchi
 */
public class SurefireArchiver extends MavenReporter {
    private TestResult result;

    public boolean preExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener) throws InterruptedException, IOException {
        if (isSurefireTest(mojo)) {
            // tell surefire:test to keep going even if there was a failure,
            // so that we can record this as yellow.
            // note that because of the way Maven works, just updating system property at this point is too late
            XmlPlexusConfiguration c = (XmlPlexusConfiguration) mojo.configuration.getChild("testFailureIgnore");
            if(c!=null && c.getValue().equals("${maven.test.failure.ignore}") && System.getProperty("maven.test.failure.ignore")==null)
                c.setValue("true");
        }
        return true;
    }

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, final BuildListener listener, Throwable error) throws InterruptedException, IOException {
        if (!isSurefireTest(mojo)) return true;

        listener.getLogger().println(Messages.SurefireArchiver_Recording());

        File reportsDir;
        try {
            reportsDir = mojo.getConfigurationValue("reportsDirectory", File.class);
        } catch (ComponentConfigurationException e) {
            e.printStackTrace(listener.fatalError(Messages.SurefireArchiver_NoReportsDir()));
            build.setResult(Result.FAILURE);
            return true;
        }

        if(reportsDir.exists()) {
            // surefire:test just skips itself when the current project is not a java project

            FileSet fs = Util.createFileSet(reportsDir,"*.xml","testng-results.xml,testng-failed.xml");
            DirectoryScanner ds = fs.getDirectoryScanner();

            if(ds.getIncludedFiles().length==0)
                // no test in this module
                return true;

            if(result==null) {
                long t = System.currentTimeMillis() - build.getMilliSecsSinceBuildStart();
                result = new TestResult(t - 1000/*error margin*/, ds);
            } else
                result.parse(build.getTimestamp().getTimeInMillis() - 1000/*error margin*/, ds);

            int failCount = build.execute(new BuildCallable<Integer, IOException>() {
                public Integer call(MavenBuild build) throws IOException, InterruptedException {
                    SurefireReport sr = build.getAction(SurefireReport.class);
                    if(sr==null)
                        build.getActions().add(new SurefireReport(build, result, listener));
                    else
                        sr.setResult(result,listener);
                    if(result.getFailCount()>0)
                        build.setResult(Result.UNSTABLE);
                    build.registerAsProjectAction(SurefireArchiver.this);
                    return result.getFailCount();
                }
            });

            // if surefire plugin is going to kill maven because of a test failure,
            // intercept that (or otherwise build will be marked as failure)
            if(failCount>0 && error instanceof MojoFailureException) {
                MavenBuilder.markAsSuccess = true;
            }
        }

        return true;
    }


    public Action getProjectAction(MavenModule module) {
        return new TestResultProjectAction(module);
    }

    private boolean isSurefireTest(MojoInfo mojo) {
        if (!mojo.is("org.apache.maven.plugins", "maven-surefire-plugin", "test"))
            return false;

        try {
            Boolean skip = mojo.getConfigurationValue("skip", Boolean.class);
            if (((skip != null) && (skip))) {
                return false;
            }

            if (mojo.pluginName.version.compareTo("2.3") >= 0) {
                Boolean skipExec = mojo.getConfigurationValue("skipExec", Boolean.class);

                if (((skipExec != null) && (skipExec))) {
                    return false;
                }
            }

            if (mojo.pluginName.version.compareTo("2.4") >= 0) {
                Boolean skipTests = mojo.getConfigurationValue("skipTests", Boolean.class);

                if (((skipTests != null) && (skipTests))) {
                    return false;
                }
            }

        } catch (ComponentConfigurationException e) {
            return false;
        }

        return true;
    }

    @Extension
    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public String getDisplayName() {
            return Messages.SurefireArchiver_DisplayName();
        }

        public SurefireArchiver newAutoInstance(MavenModule module) {
            return new SurefireArchiver();
        }
    }

    private static final long serialVersionUID = 1L;
}
