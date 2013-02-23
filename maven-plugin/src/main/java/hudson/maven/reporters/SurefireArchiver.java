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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

/**
 * Records the surefire test result.
 * @author Kohsuke Kawaguchi
 * @author Christoph Kutzinski
 */
public class SurefireArchiver extends TestFailureDetector {
    private transient TestResult result;
    private final AtomicBoolean hasTestFailures = new AtomicBoolean();
    
    /**
     * Store result files already parsed, so we don't parse them again,
     * if a later running mojo specifies the same reports directory.
     */
    private transient ConcurrentMap<File, File> parsedFiles = new ConcurrentHashMap<File,File>();
    
    @Override
    public boolean hasTestFailures() {
        return hasTestFailures.get();
    }

    public boolean preExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener) throws InterruptedException, IOException {
        if (isTestMojo(mojo)) {
            // tell test mojo to keep going even if there was a failure,
            // so that we can record this as yellow.
            // note that because of the way Maven works, just updating system property at this point is too late
            
            // Many test plugins have - as surefire - a configuration key 'testFailureIgnore' which defaults to
            // ${maven.test.failure.ignore}, so just try that one and change value to true,
            // if it's still at that default:
            XmlPlexusConfiguration c = (XmlPlexusConfiguration) mojo.configuration.getChild("testFailureIgnore",false);
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
        return true;
    }

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, final BuildListener listener, Throwable error) throws InterruptedException, IOException {
        TestMojo testMojo = getTestMojo(mojo);
        if (testMojo == null) return true;

        listener.getLogger().println(Messages.SurefireArchiver_Recording());

        Iterable<File> fileSet;
        
        try {
            fileSet = testMojo.getReportFiles(pom, mojo);
        } catch (ComponentConfigurationException e) {
            e.printStackTrace(listener.fatalError(Messages.SurefireArchiver_NoReportsDir()));
            build.setResult(Result.FAILURE);
            return true;
        }
        
        if(fileSet != null) {
            synchronized (build) {
            	
                if(result==null)    result = new TestResult();
                
                // filter all the already parsed files:
                fileSet = Iterables.filter(fileSet, new Predicate<File>() {
                    @Override
                    public boolean apply(File input) {
                        return !parsedFiles.containsKey(input);
                    }
                });
                
                if (!fileSet.iterator().hasNext())
                    return true;
                
                result.parse(System.currentTimeMillis() - build.getMilliSecsSinceBuildStart(), fileSet);
                // TODO kutzi: the following is a 'more correct' way to get the reports associated to a mojo,
                // but needs more testing
//                Iterable<File> reportFilesFiltered = getFilesBetween(reportsDir, reportFiles, mojo.getStartTime(), System.currentTimeMillis());
//                result.parse(reportFilesFiltered);
                
                
                rememberCheckedFiles(fileSet);
                
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
     * Add checked files to the exclude list of the fileSet
     */
    private void rememberCheckedFiles(Iterable<File> fileSet) {
        for (File f : fileSet) {
            this.parsedFiles.put(f, f);
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

//    private static Iterable<File> getFilesBetween(final File reportsDir,
//            final String[] reportFiles, final long from, final long to) {
//        return new FilteredReportsFileIterable(reportsDir, reportFiles, from, to);
//    }
    
    /**
     * Provides an {@link Iterable} view on the reports files while filtering out all files
     * which don't have a lastModified time in between from and to.
     */
    static class FilteredReportsFileIterable implements Iterable<File> {
        private final File reportsDir;
        private final String[] reportFiles;
        private final long from;
        private final long to;

        FilteredReportsFileIterable(File reportsDir,
                String[] reportFiles, long from, long to) {
            this.reportsDir = reportsDir;
            this.reportFiles = reportFiles;
            
            // FAT filesystems have a max resolution of 2 seconds so we need to subtract/add 2 seconds to
            // the range borders.
            // All other fs should have a equal or better precision
            this.from = from - 2000;
            this.to = to + 2000;
        }

        @Override
        public Iterator<File> iterator() {
            
            Predicate<File> fileWithinFromAndTo = new Predicate<File>() {
                @Override
                public boolean apply(File file) {
                    long lastModified = file.lastModified();
                    if (lastModified>=from && lastModified<=to) {
                        return true;
                    }
                    return false;
                }
            };
            
            return Iterators.filter(
                    Iterators.transform(
                        Iterators.forArray(reportFiles),
                        new Function<String, File>() {
                            @Override
                            public File apply(String file) {
                                return getFile(reportsDir,file);
                            }
                        }),
                    fileWithinFromAndTo);
        }
        
        // here for mocking purposes:
        File getFile(File parent, String child) {
            return new File(parent,child);
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
        return getTestMojo(mojo) != null;
    }
    
    private TestMojo getTestMojo(MojoInfo mojo) {
        TestMojo testMojo = TestMojo.lookup(mojo);

        if (testMojo == null)
            return null;
        
        try {
            // most test plugins have at least on of the test-skip properties:
            String[] skipProperties = {"skip", "skipExec", "skipTests", "skipTest"};
            for (String skipProperty : skipProperties) {
                Boolean skip = mojo.getConfigurationValue(skipProperty, Boolean.class);
                if (((skip != null) && (skip))) {
                    return null;
                }
            }
        } catch (ComponentConfigurationException e) {
            return null;
        }

        return testMojo;
    }
    
    // I'm not sure if SurefireArchiver is actually ever (de-)serialized,
    // but just to be sure, set fileSets here
    protected Object readResolve() {
        parsedFiles = new ConcurrentHashMap<File,File>();
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
