/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MojoInfo;
import hudson.maven.MavenBuild;
import hudson.model.BuildListener;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReport;

import java.io.IOException;
import java.io.File;
import java.util.Locale;

/**
 * Watches out for executions of {@link MavenReport} mojos and record its output.
 * 
 * @author Kohsuke Kawaguchi
 */
public class ReportCollector extends MavenReporter {
    private transient ReportAction action;

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener, Throwable error) throws InterruptedException, IOException {
        if(!(mojo.mojo instanceof MavenReport))
            return true;    // not a maven report

        MavenReport report = (MavenReport)mojo.mojo;

        String reportPath = report.getReportOutputDirectory().getPath();
        String projectReportPath = pom.getReporting().getOutputDirectory();
        if(!reportPath.startsWith(projectReportPath)) {
            // report is placed outside site. Can't record it.
            listener.getLogger().println(Messages.ReportCollector_OutsideSite(reportPath,projectReportPath));
            return true;
        }

        if(action==null)
            action = new ReportAction();


        // this is the entry point to the report
        File top = new File(report.getReportOutputDirectory(),report.getOutputName()+".html");
        String relPath = top.getPath().substring(projectReportPath.length());

        action.add(new ReportAction.Entry(relPath,report.getName(Locale.getDefault())));
        
        return true;
    }

    public boolean leaveModule(MavenBuildProxy build, MavenProject pom, BuildListener listener) throws InterruptedException, IOException {
        if(action!=null) {
            // TODO: archive pom.getReporting().getOutputDirectory()
            build.executeAsync(new AddActionTask(action));
        }
        action = null;
        return super.leaveModule(build, pom, listener);
    }

    private static final class AddActionTask implements MavenBuildProxy.BuildCallable<Void,IOException> {
        private final ReportAction action;

        public AddActionTask(ReportAction action) {
            this.action = action;
        }

        public Void call(MavenBuild build) throws IOException, InterruptedException {
            build.addAction(action);
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        public String getDisplayName() {
            return Messages.ReportCollector_DisplayName();
        }

        public ReportCollector newAutoInstance(MavenModule module) {
            return new ReportCollector();
        }
    }

    private static final long serialVersionUID = 1L;
}
