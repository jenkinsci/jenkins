/*
 * The MIT License
 *
 * Copyright (c) 2013 Red Hat, Inc.
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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.Run.RunnerAbortedException;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.test.MatrixTestResult;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Set;

import javax.annotation.Nonnull;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

/**
 * Create Test Result action as soon as the build starts to display realtime test results.
 *
 * @author ogondza
 * @since TODO
 */
public class TestResultActionUpdater extends BuildWrapper {

    @Override
    public Environment setUp(AbstractBuild b, Launcher launcher, final BuildListener listener) throws IOException, InterruptedException, RunnerAbortedException {

        final AbstractBuild<?, ?> build = b;

        if (!isApplicable(build)) return new Environment() {};

        final TestResultUpdater.RemoteHandler threadHandler = build.getWorkspace()
                .act(new StartTheThread(new BuildProxy(build), listener))
        ;

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {

                threadHandler.terminate();
                return true;
            }
        };
    }

    private static boolean isApplicable(AbstractBuild<?, ?> build) {

        // Aggregate results from MatrixRuns
        if (build instanceof MatrixBuild) return false;

        if (getArchiver(getProject(build)) == null) return false;

        return true;
    }

    private static JUnitResultArchiver getArchiver(AbstractProject<?, ?> project) {
        return project.getPublishersList().get(JUnitResultArchiver.class);
    }

    private static AbstractProject<?, ?> getProject(AbstractBuild<?, ?> build) {

        if (build instanceof MatrixRun) return getProject(((MatrixRun) build).getRootBuild());

        return build.getProject();
    }

    /**
     * Start the thread on slave and send home the remotable proxy to kill the thread later.
     *
     * @author ogondza
     */
    private static final class StartTheThread implements FilePath.FileCallable<TestResultUpdater.RemoteHandler> {
        private final @Nonnull BuildProxy build;
        private final @Nonnull BuildListener listener;

        private StartTheThread(final @Nonnull BuildProxy build, final @Nonnull BuildListener listener) {
            this.build = build;
            this.listener = listener;
        }

        public TestResultUpdater.RemoteHandler invoke(final File workspace, final VirtualChannel channel) throws IOException, InterruptedException {
            final Thread thread = new Thread(build, listener);
            thread.start();
            return channel.export(TestResultUpdater.RemoteHandler.class, thread.handler());
        }
    }

    private static final class BuildProxy implements Serializable, Cloneable {
        private static final long serialVersionUID = -5434762842860565234L;

        private final @Nonnull String workspace;
        private final @Nonnull String externalizableId;
        private final @Nonnull String fullDisplayName;
        private final @Nonnull String reportFiles;
        private final boolean keepLongStdio;

        /**
         * Channel to master.
         *
         * Local channel on master, real channel on slave (#readObject(java.io.ObjectInputStream)).
         *
         * This field is otherwise final.
         */
        private transient VirtualChannel masterChannel = Jenkins.MasterComputer.localChannel;

        /**
         * This field is initialized to build time according to master's clock.
         * Just before the instance is serialized its value is converted to
         * build age and after deserializing it is converted to build time
         * according to slave's clock. To do this, #writeObject(java.io.ObjectOutputStream)
         * and #readObject(java.io.ObjectInputStream) are used as hook methods.
         * From user's perspective the value should relate to the correct clock
         * all the time.
         *
         * This field is otherwise final.
         */
        private long buildTime;

        // This is evaluated on Master. No reference to build should be persisted
        public BuildProxy(final @Nonnull AbstractBuild<?, ?> build) {
            final AbstractProject<?, ?> project = getProject(build);

            this.workspace = build.getWorkspace().getRemote();
            this.externalizableId = build.getExternalizableId();
            this.fullDisplayName = build.getFullDisplayName();
            this.buildTime = build.getTimestamp().getTimeInMillis();

            final JUnitResultArchiver archiver = getArchiver(project);
            this.reportFiles = archiver.getTestResults();
            this.keepLongStdio = archiver.isKeepLongStdio();
        }

        public long getBuildTime() {
            return buildTime;
        }

        public @Nonnull String getReportFiles() {
            return reportFiles;
        }

        public boolean isKeepLongStdio() {
            return keepLongStdio;
        }

        public @Nonnull File getWorkspace() {
            return new File(workspace);
        }

        public @Nonnull String getFullDisplayName() {
            return fullDisplayName;
        }

        public <V, T extends Throwable> V execute(
                @Nonnull BuildCallable<V, T> command
        ) throws T, IOException, InterruptedException{

            return masterChannel.call(
                    new ProxyRemoteCommand<V, T>(command, externalizableId)
            );
        }

        @SuppressWarnings("unused")
        private Object writeReplace() throws java.io.ObjectStreamException {
            // Manipulate buildTime in cloned instance instead of this one so the
            // original object will not be corrupted after serializing it.
            // #writeObject(java.io.ObjectOutputStream out) wouldn't work.
            try {
                final BuildProxy clone = (BuildProxy) clone();
                clone.buildTime -= System.currentTimeMillis();
                return clone;
            } catch (CloneNotSupportedException ex) {

                throw new AssertionError(ex);
            }
        }

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            buildTime += System.currentTimeMillis();
            masterChannel = Channel.current();
        }
    }

    private static final class ProxyRemoteCommand<V, T extends Throwable> implements Callable<V, T> {
        private static final long serialVersionUID = -619687758694749326L;

        private final @Nonnull BuildCallable<V, T> command;
        private final @Nonnull String externalizableId;

        private ProxyRemoteCommand(@Nonnull BuildCallable<V, T> program, @Nonnull String externalizableId) {
            this.command = program;
            this.externalizableId = externalizableId;
        }

        public V call() throws T {
            final AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) Run.fromExternalizableId(externalizableId);
            return command.call(build);
        }
    }

    static interface BuildCallable<V,T extends Throwable> extends Serializable {
        /**
         * Performs computation and returns the result, or throws some exception.
         *
         * @throws InterruptedException
         *      if the processing is interrupted in the middle. Exception will be
         *      propagated to the caller.
         * @throws IOException
         *      if the program simply wishes to propagate the exception, it may throw
         *      {@link IOException}.
         */
        V call(@Nonnull AbstractBuild<?, ?> build) throws T;
    }

    private static final class Thread extends TestResultUpdater {

        private final @Nonnull BuildProxy build;

        private Thread(final @Nonnull BuildProxy build, final @Nonnull BuildListener listener) {
            super(listener, build.getFullDisplayName());
            this.build = build;
        }

        @Override
        protected boolean keepLongStdio() {
            return build.isKeepLongStdio();
        }

        @Override
        protected long buildTime() {
            return build.getBuildTime();
        }

        @Override
        protected @Nonnull Iterable<File> reportFiles(final Set<String> parsedFiles) {
            final String[] fileNames = Util.createFileSet(build.getWorkspace(), build.getReportFiles())
                    .getDirectoryScanner()
                    .getIncludedFiles()
            ;

            final ArrayList<File> files = new ArrayList<File>(fileNames.length);
            for (final String filename: fileNames) {

                final File file = new File(build.getWorkspace(), filename);
                if (parsedFiles.contains(file.getAbsolutePath())) continue;
                files.add(file);
            }

            return files;
        }

        @Override
        protected void update(final @Nonnull TestResult testResult) {
            try {
                build.execute(new UpdateCommand(testResult, listener));
            } catch (IOException ex) {

                ex.printStackTrace(listener.error("Unable to update test results"));
            } catch (InterruptedException ex) {

                ex.printStackTrace(listener.error("Unable to update test results"));
            }
        }
    }

    private static final class UpdateCommand implements BuildCallable<Void, IOException> {
        private static final long serialVersionUID = 4820144835418301518L;

        private final @Nonnull TestResult testResult;
        private final @Nonnull BuildListener listener;

        private UpdateCommand(@Nonnull TestResult testResult, @Nonnull BuildListener listener) {
            this.testResult = testResult;
            this.listener = listener;
        }

        public Void call(@Nonnull AbstractBuild<?, ?> build) throws IOException {

            TestResultAction action = build.getAction(TestResultAction.class);
            if (action == null) {
                action = new TestResultAction(build, testResult, listener);
                build.addAction(action);

                if (build instanceof MatrixRun) {
                    final MatrixBuild mb = ((MatrixRun) build).getParentBuild();
                    MatrixTestResult aggregatedResult = mb.getAction(MatrixTestResult.class);
                    if (aggregatedResult == null) {
                        aggregatedResult = new MatrixTestResult(mb);
                        mb.addAction(aggregatedResult);
                    }
                }
            } else {

              action.updateResult(testResult, listener);
            }

            if (build instanceof MatrixRun) {
                ((MatrixRun) build).getParentBuild().getAction(MatrixTestResult.class).update();
            }

            return null;
        }
    }

    @Override
    public TestResultActionUpdater.Descriptor getDescriptor() {

        return (TestResultActionUpdater.Descriptor) super.getDescriptor();
    }

    @Extension
    public static class Descriptor extends BuildWrapperDescriptor {

        @Override
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {

            return new TestResultActionUpdater();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> job) {

            return job.getPublishersList().get(JUnitResultArchiver.class) != null;
        }

        @Override
        public String getDisplayName() {
            return "Report test result in realtime";
        }
    }
}