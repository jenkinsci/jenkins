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
package hudson.tasks.test;

import hudson.Launcher;
import hudson.Util;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormFieldValidator;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Aggregates downstream test reports into a single consolidated report,
 * so that people can see the overall test results in one page
 * when tests are scattered across many different jobs.
 *
 * @author Kohsuke Kawaguchi
 */
public class AggregatedTestResultPublisher extends Recorder {
    /**
     * Jobs to aggregate. Camma separated.
     * Null if triggering downstreams.
     */
    public final String jobs;

    public AggregatedTestResultPublisher(String jobs) {
        this.jobs = Util.fixEmptyAndTrim(jobs);
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        // add a TestResult just so that it can show up later.
        build.addAction(new TestResultAction(jobs,build));
        return true;
    }

    /**
     * Action that serves the aggregated record.
     *
     * TODO: persist some information so that even when some of the individuals
     * are gone, we can still retain some useful information.
     */
    public static final class TestResultAction extends AbstractTestResultAction {
        /**
         * Jobs to aggregate. Camma separated.
         * Never null.
         */
        private final String jobs;

        /**
         * The last time the fields of this object is computed from the rest.
         */
        private transient long lastUpdated = 0;
        /**
         * When was the last time any build completed?
         */
        private static long lastChanged = 0;

        private transient int failCount;
        private transient int totalCount;
        private transient List<AbstractTestResultAction> individuals;
        /**
         * Projects that haven't run yet.
         */
        private transient List<AbstractProject> didntRun;

        public TestResultAction(String jobs, AbstractBuild<?,?> owner) {
            super(owner);
            if(jobs==null) {
                // resolve null as the transitive downstream jobs
                StringBuilder buf = new StringBuilder();
                for (AbstractProject p : getProject().getTransitiveDownstreamProjects()) {
                    if(buf.length()>0)  buf.append(',');
                    buf.append(p.getFullName());
                }
                jobs = buf.toString();
            }
            this.jobs = jobs;
        }

        /**
         * Gets the jobs to be monitored.
         */
        public Collection<AbstractProject> getJobs() {
            List<AbstractProject> r = new ArrayList<AbstractProject>();
            for (String job : Util.tokenize(jobs,",")) {
                AbstractProject j = Hudson.getInstance().getItemByFullName(job.trim(), AbstractProject.class);
                if(j!=null)
                    r.add(j);
            }
            return r;
        }

        private AbstractProject<?,?> getProject() {
            return owner.getProject();
        }

        public int getFailCount() {
            upToDateCheck();
            return failCount;
        }

        public int getTotalCount() {
            upToDateCheck();
            return totalCount;
        }

        public Object getResult() {
            upToDateCheck();
            return this;
        }

        /**
         * Returns the individual test results that are aggregated.
         */
        public List<AbstractTestResultAction> getIndividuals() {
            upToDateCheck();
            return Collections.unmodifiableList(individuals);
        }

        /**
         * Gets the downstream projects that haven't run yet, but
         * expected to produce test results.
         */
        public List<AbstractProject> getDidntRun() {
            return Collections.unmodifiableList(didntRun);
        }

        /**
         * Makes sure that the data fields are up to date.
         */
        private synchronized void upToDateCheck() {
            // up to date check
            if(lastUpdated>lastChanged)     return;
            lastUpdated = lastChanged+1;

            int failCount = 0;
            int totalCount = 0;
            List<AbstractTestResultAction> individuals = new ArrayList<AbstractTestResultAction>();
            List<AbstractProject> didntRun = new ArrayList<AbstractProject>();

            for (AbstractProject job : getJobs()) {
                RangeSet rs = owner.getDownstreamRelationship(job);
                if(rs.isEmpty()) {
                    // is this job expected to produce a test result?
                    Run b = job.getLastSuccessfulBuild();
                    if(b!=null && b.getAction(AbstractTestResultAction.class)!=null)
                        didntRun.add(job);
                    continue;
                }
                for (int n : rs.listNumbersReverse()) {
                    Run b = job.getBuildByNumber(n);
                    if(b==null) continue;
                    if(b.isBuilding() || b.getResult().isWorseThan(Result.UNSTABLE))
                        continue;   // don't count them

                    for( AbstractTestResultAction ta : b.getActions(AbstractTestResultAction.class)) {
                        failCount += ta.getFailCount();
                        totalCount += ta.getTotalCount();
                        individuals.add(ta);
                    }
                    break;
                }
            }
            
            this.failCount = failCount;
            this.totalCount = totalCount;
            this.individuals = individuals;
            this.didntRun = didntRun;
        }

        @Override
        public String getDisplayName() {
            return Messages.AggregatedTestResultPublisher_Title();
        }

        @Override
        public String getUrlName() {
            return "aggregatedTestReport";
        }

        @Extension
        public static class RunListenerImpl extends RunListener<Run> {
            public RunListenerImpl() {
                super(Run.class);
            }

            public void onCompleted(Run run, TaskListener listener) {
                lastChanged = System.currentTimeMillis();
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;    // for all types
        }

        public String getDisplayName() {
            return Messages.AggregatedTestResultPublisher_DisplayName();
        }

        public String getHelpFile() {
            return "/help/tasks/aggregate-test/help.html";
        }

        public void doCheck(StaplerRequest req, StaplerResponse rsp, @QueryParameter final String value) throws IOException, ServletException {
            // Require CONFIGURE permission on this project
            AbstractProject project = req.findAncestorObject(AbstractProject.class);

            new FormFieldValidator(req,rsp,project,Item.CONFIGURE) {
                protected void check() throws IOException, ServletException {
                    for (String name : Util.tokenize(value, ",")) {
                        name = name.trim();
                        if(Hudson.getInstance().getItemByFullName(name)==null) {
                            error(hudson.tasks.Messages.BuildTrigger_NoSuchProject(name,AbstractProject.findNearest(name).getName()));
                        }
                    }
                }
            }.process();
        }

        public AggregatedTestResultPublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            JSONObject s = formData.getJSONObject("specify");
            if(s.isNullObject())
                return new AggregatedTestResultPublisher(null);
            else
                return new AggregatedTestResultPublisher(s.getString("jobs"));
        }
    }

}
