/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package org.jvnet.hudson.test;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.RunAction;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Utility to determine when a build record is loaded.
 * @since 1.517
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // API design mistakes
public final class RunLoadCounter {

    private static final ThreadLocal<String> currProject = new ThreadLocal<String>();
    private static final ThreadLocal<AtomicInteger> currCount = new ThreadLocal<AtomicInteger>();

    /**
     * Prepares a new project to be measured.
     * Usually called before starting builds, but may also be called retroactively.
     * @param project a project of any kind
     * @throws IOException if preparations fail
     */
    public static void prepare(AbstractProject<?,?> project) throws IOException {
        project.getPublishersList().add(new MarkerAdder());
        for (AbstractBuild<?,?> build : project._getRuns()) {
            Marker.add(build);
            build.save();
        }
    }

    /**
     * Counts how many build records are loaded as a result of some task.
     * @param project a project on which {@link #prepare} was called prior to creating builds
     * @param thunk a task which is expected to load some build records
     * @return how many build records were actually {@linkplain Run#onLoad loaded} as a result
     */
    public static int countLoads(AbstractProject<?,?> project, Runnable thunk) {
        project._getRuns().purgeCache();
        currProject.set(project.getFullName());
        currCount.set(new AtomicInteger());
        thunk.run();
        return currCount.get().get();
    }

    /**
     * Asserts that at most a certain number of build records are loaded as a result of some task.
     * @param project a project on which {@link #prepare} was called prior to creating builds
     * @param max the maximum number of build records we expect to load
     * @param thunk a task which is expected to load some build records
     * @return the result of the task, if any
     * @throws Exception if the task failed
     * @throws AssertionError if one more than max build record is loaded
     * @param <T> the return value type
     */
    public static <T> T assertMaxLoads(AbstractProject<?,?> project, int max, Callable<T> thunk) throws Exception {
        project._getRuns().purgeCache();
        currProject.set(project.getFullName());
        currCount.set(new AtomicInteger(-(max + 1)));
        return thunk.call();
    }

    private RunLoadCounter() {}

    /**
     * Used internally.
     */
    @Restricted(NoExternalUse.class)
    public static final class Marker implements RunAction {

        static void add(AbstractBuild<?,?> build) {
            build.addAction(new Marker(build.getParent().getFullName(), build.getNumber()));
        }

        private final String project;
        private final int build;

        Marker(String project, int build) {
            this.project = project;
            this.build = build;
        }

        @Override public void onLoad() {
            if (project.equals(currProject.get())) {
                System.err.println("loaded " + project + " #" + build);
                assert currCount.get().incrementAndGet() != 0 : "too many build records loaded from " + project;
            }
        }

        @Override public void onAttached(Run r) {}

        @Override public void onBuildComplete() {}

        @Override public String getIconFileName() {
            return null;
        }

        @Override public String getDisplayName() {
            return null;
        }

        @Override public String getUrlName() {
            return null;
        }

    }

    /**
     * Used internally.
     */
    @Restricted(NoExternalUse.class)
    public static final class MarkerAdder extends Notifier {

        @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            Marker.add(build);
            return true;
        }

        @Override public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }

    }

}
