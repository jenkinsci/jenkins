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

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.maven.Maven3Builder;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildInformation;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.maven.MavenBuilder;
import hudson.maven.MavenModule;
import hudson.maven.MavenProjectActionBuilder;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.TestResultProjectAction;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Records the surefire test result.
 * @author Kohsuke Kawaguchi
 * @author Christoph Kutzinski
 */
public class SurefireArchiver extends TestFailureDetector {
    private transient TestResult result;
    private final AtomicBoolean hasTestFailures = new AtomicBoolean();
    
    /**
     * Store the filesets here as we want to track ignores between multiple runs of this class<br/>
     * Note: Because this class can be run with different mojo goals with different path settings, 
     * we track multiple {@link FileSet}s for each encountered <tt>reportsDir</tt>
     */
    private transient ConcurrentMap<File, FileSet> fileSets = new ConcurrentHashMap<File,FileSet>();
    
    @Override
    public boolean hasTestFailures() {
        return hasTestFailures.get();
    }

    public boolean preExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener) throws InterruptedException, IOException {
        if (isTestMojo(mojo)) {
            if (!isSoapUiMojo(mojo)) {
                // tell surefire:test to keep going even if there was a failure,
                // so that we can record this as yellow.
                // note that because of the way Maven works, just updating system property at this point is too late
                XmlPlexusConfiguration c = (XmlPlexusConfiguration) mojo.configuration.getChild("testFailureIgnore");
                if(c!=null && c.getValue() != null && c.getValue().equals("${maven.test.failure.ignore}") && System.getProperty("maven.test.failure.ignore")==null) {
                    if (build.getMavenBuildInformation().isMaven3OrLater()) {
                        String fieldName = "testFailureIgnore";
                        if (mojo.mojoExecution.getConfiguration().getChild( fieldName ) != null) {
                          mojo.mojoExecution.getConfiguration().getChild( fieldName ).setValue( Boolean.TRUE.toString() );
                        } else {
                            Xpp3Dom child = new Xpp3Dom( fieldName );
                            child.setValue( Boolean.TRUE.toString() );
                            mojo.mojoExecution.getConfiguration().addChild( child );
                        }
                        
                    } else {
                        c.setValue(Boolean.TRUE.toString());
                    }
                }
            }
        }
        return true;
    }

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, final BuildListener listener, Throwable error) throws InterruptedException, IOException {
        if (!isTestMojo(mojo)) return true;

        listener.getLogger().println(Messages.SurefireArchiver_Recording());

        File reportsDir;
        
        try {
            // seems like almost all test mojos have this config value:
            reportsDir = mojo.getConfigurationValue("reportsDirectory", File.class);
        } catch (ComponentConfigurationException e) {
            e.printStackTrace(listener.fatalError(Messages.SurefireArchiver_NoReportsDir()));
            build.setResult(Result.FAILURE);
            return true;
        }
        
        if (reportsDir == null) {
            // if test mojo doesn't have such config value, still almost all
            // default to target/surefire-reports
            reportsDir = new File(pom.getBasedir(), "target/surefire-reports");
        }

        if(reportsDir.exists()) {
            // surefire:test just skips itself when the current project is not a java project

            synchronized (build) {
            	FileSet fileSet = getFileSet(reportsDir);
            	
                DirectoryScanner ds = fileSet.getDirectoryScanner();
                
                if(ds.getIncludedFilesCount()==0)
                    // no test in this module
                    return true;
                
                String[] reportFiles = ds.getIncludedFiles();
                rememberCheckedFiles(reportsDir, reportFiles);
    
                if(result==null)    result = new TestResult();
                result.parse(System.currentTimeMillis() - build.getMilliSecsSinceBuildStart(), reportsDir, reportFiles);
                
                // final reference in order to serialize it:
                final TestResult r = result;
                
                int failCount = build.execute(new BuildCallable<Integer, IOException>() {
                        private static final long serialVersionUID = -1023888330720922136L;

                        public Integer call(MavenBuild build) throws IOException, InterruptedException {
                            SurefireReport sr = build.getAction(SurefireReport.class);
                            if(sr==null)
                                build.getActions().add(new SurefireReport(build, r, listener));
                            else
                                sr.setResult(r,listener);
                            if(r.getFailCount()>0)
                                build.setResult(Result.UNSTABLE);
                            build.registerAsProjectAction(new FactoryImpl());
                            return r.getFailCount();
                        }
                    });
                
                // if surefire plugin is going to kill maven because of a test failure,
                // intercept that (or otherwise build will be marked as failure)
                if(failCount>0) {
                    markBuildAsSuccess(error,build.getMavenBuildInformation());
                    hasTestFailures.set(true);
                }
            }
        }

        return true;
    }
    
    @Override
    public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        //Discard unneeded test result objects so they can't waste memory
        for(MavenReporter reporter: build.getProject().getReporters()) {
            if(reporter instanceof SurefireArchiver) {
                SurefireArchiver surefireReporter = (SurefireArchiver) reporter;
                if(surefireReporter.result != null) {
                    surefireReporter.result = null;
                }
            }
        }    
        return true;
    }
    
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification="It's okay to write to static fields here, as each Maven build is started in its own VM")
    private void markBuildAsSuccess(Throwable mojoError, MavenBuildInformation buildInfo) {
        if(mojoError == null // in the success case we don't get any exception in Maven 3.0.2+; Maven < 3.0.2 returns no exception anyway
           || mojoError instanceof MojoFailureException) {
            MavenBuilder.markAsSuccess = true;
            Maven3Builder.markAsSuccess = true;
        }
    }
    
    /**
     * Returns the appropriate FileSet for the selected baseDir
     * @param baseDir
     * @return
     */
    FileSet getFileSet(File baseDir) {
    	FileSet fs = fileSets.get(baseDir);
    	if (fs == null) {
    		fs = Util.createFileSet(baseDir, "*.xml","testng-results.xml,testng-failed.xml");
    		FileSet previous = fileSets.putIfAbsent(baseDir, fs);
    		if (previous != null) {
    		    return previous;
    		}
    	}
    	
    	return fs;
    }
    
    /**
     * Add checked files to the exclude list of the fileSet
     */
    private void rememberCheckedFiles(File baseDir, String[] reportFiles) {
    	FileSet fileSet = getFileSet(baseDir);
    	
    	for (String file : reportFiles) {
    		fileSet.createExclude().setName(file);
    	}
    }

    /**
     * Up to 1.372, there was a bug that causes Hudson to persist {@link SurefireArchiver} with the entire test result
     * in it. If we are loading those, fix it up in memory to reduce the memory footprint.
     *
     * It'd be nice we can save the record to remove problematic portion, but that might have
     * additional side effect.
     */
    public static void fixUp(List<MavenProjectActionBuilder> builders) {
        if (builders==null) return;
        for (ListIterator<MavenProjectActionBuilder> itr = builders.listIterator(); itr.hasNext();) {
            MavenProjectActionBuilder b =  itr.next();
            if (b instanceof SurefireArchiver)
                itr.set(new FactoryImpl());
        }
    }

    /**
     * Part of the serialization data attached to {@link MavenBuild}.
     */
    static final class FactoryImpl implements MavenProjectActionBuilder {
        public Collection<? extends Action> getProjectActions(MavenModule module) {
            return Collections.singleton(new TestResultProjectAction(module));
        }
    }

    boolean isTestMojo(MojoInfo mojo) {
        if (!isSurefireOrFailsafeMojo(mojo)
            && !isAndroidMojo(mojo)
            && !goalIsIn(mojo, "test", "test-run", "toolkit-resolve-test")) { 
            return false;
        }

        if (isAndroidMojo(mojo)) {
            if (mojo.pluginName.version.compareTo("3.0.0-alpha-6") < 0) {
                // Earlier versions do not support tests
                return false;
            }
        }
            
        if (mojo.is("org.codehaus.mojo", "gwt-maven-plugin", "test") && mojo.pluginName.version.compareTo("1.2") < 0) {
            // gwt-maven-plugin < 1.2 does not implement required Surefire option
            return false;
        }
        
        try {
           Boolean skip = mojo.getConfigurationValue("skip", Boolean.class);
            if (((skip != null) && (skip))) {
                return false;
            }
            
            Boolean skipExec = mojo.getConfigurationValue("skipExec", Boolean.class);
            if (((skipExec != null) && (skipExec))) {
                return false;
            }
            Boolean skipTests = mojo.getConfigurationValue("skipTests", Boolean.class);
                
            if (((skipTests != null) && (skipTests))) {
                return false;
            }
            Boolean skipTest = mojo.getConfigurationValue("skipTest", Boolean.class);
            if (((skipTest != null) && (skipTest))) {
                return false;
            }
        } catch (ComponentConfigurationException e) {
            return false;
        }

        return true;
    }
    
    private boolean goalIsIn(MojoInfo mojo, String... goalNames) {
        for (String name : goalNames) {
            if (mojo.getGoal().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAndroidMojo(MojoInfo mojo) {
      return mojo.is("com.jayway.maven.plugins.android.generation2", "maven-android-plugin", "internal-integration-test")
          || mojo.is("com.jayway.maven.plugins.android.generation2", "android-maven-plugin", "internal-integration-test");
    }

    private boolean isSurefireOrFailsafeMojo(MojoInfo mojo) {
      return mojo.is("org.apache.maven.plugins", "maven-surefire-plugin", "test")
          || mojo.is("org.apache.maven.plugins", "maven-failsafe-plugin", "verify");
    }
    
    private boolean isSoapUiMojo(MojoInfo mojo) {
      return mojo.is("eviware", "maven-soapui-plugin", "test")
          || mojo.is("eviware", "maven-soapui-pro-plugin", "test");
    }
    
    // I'm not sure if SurefireArchiver is actually ever (de-)serialized,
    // but just to be sure, set fileSets here
    protected Object readResolve() {
        fileSets = new ConcurrentHashMap<File,FileSet>();
        return this;
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
