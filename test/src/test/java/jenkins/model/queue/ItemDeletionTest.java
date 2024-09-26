/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

package jenkins.model.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;

import hudson.Extension;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.listeners.RunListener;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.SortedMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jenkins.model.Jenkins;
import jenkins.model.lazy.LazyBuildMixIn;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class ItemDeletionTest {
    private static final Logger LOGGER = Logger.getLogger(ItemDeletionTest.class.getName());

    @Rule
    public BuildWatcher watcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void waitsForAsynchronousExecutions() throws Throwable {
        var p = r.createProject(AsyncJob.class);
        var b = (AsyncRun) Jenkins.get().getQueue().schedule2(p, 0).getItem().getFuture().waitForStart();
        r.waitForMessage("Starting " + b, b);
        LOGGER.info(() -> "Deleting " + p);
        p.delete();
        LOGGER.info(() -> "Deleted " + p);
        assertThat(r.jenkins.getItemByFullName(p.getFullName()), nullValue());
        var executables = Stream.of(r.jenkins.getComputers())
                .flatMap(c -> c.getAllExecutors().stream())
                .map(Executor::getCurrentExecutable)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        assertThat(executables, empty());
    }

    public static class AsyncJob extends Job<AsyncJob, AsyncRun> implements LazyBuildMixIn.LazyLoadingJob<AsyncJob, AsyncRun>, TopLevelItem, Queue.FlyweightTask {
        private transient final LazyBuildMixIn<AsyncJob, AsyncRun> buildMixIn = new LazyBuildMixIn<AsyncJob, AsyncRun>() {
            @Override protected AsyncJob asJob() {
                return AsyncJob.this;
            }
            @Override protected Class<AsyncRun> getBuildClass() {
                return AsyncRun.class;
            }
        };

        public AsyncJob(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        public boolean isBuildable() {
            return true;
        }

        @Override
        protected SortedMap _getRuns() {
            return buildMixIn._getRuns();
        }

        @Override
        protected void removeRun(AsyncRun run) {
            buildMixIn.removeRun(run);
        }

        @Override
        public LazyBuildMixIn<AsyncJob, AsyncRun> getLazyBuildMixIn() {
            return buildMixIn;
        }

        @Override public void onCreatedFromScratch() {
            super.onCreatedFromScratch();
            buildMixIn.onCreatedFromScratch();
        }

        @Override public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
            super.onLoad(parent, name);
            buildMixIn.onLoad(parent, name);
        }

        @Override
        public TopLevelItemDescriptor getDescriptor() {
            return (DescriptorImpl) Jenkins.get().getDescriptorOrDie(AsyncJob.class);
        }

        @Override
        public Queue.Executable createExecutable() throws IOException {
            return new AsyncRun(this);
        }

        @Extension
        public static class DescriptorImpl extends TopLevelItemDescriptor {
            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new AsyncJob(parent, name);
            }
        }
    }

    public static class AsyncRun extends Run<AsyncJob, AsyncRun> implements LazyBuildMixIn.LazyLoadingRun<AsyncJob, AsyncRun>, Queue.Executable {
        private transient final LazyBuildMixIn.RunMixIn<AsyncJob, AsyncRun> runMixIn = new LazyBuildMixIn.RunMixIn<AsyncJob, AsyncRun>() {
            @Override protected AsyncRun asRun() {
                return AsyncRun.this;
            }
        };

        public enum State {
            NOT_STARTED,
            BUILDING,
            COMPLETED,
        }

        private State state = State.NOT_STARTED;
        private StreamTaskListener listener;
        private Thread cancellationTask;

        public AsyncRun(AsyncJob job) throws IOException {
            super(job);
        }

        @Override
        public LazyBuildMixIn.RunMixIn<AsyncJob, AsyncRun> getRunMixIn() {
            return runMixIn;
        }

        @Override
        public boolean isBuilding() {
            return state != State.COMPLETED;
        }

        @Override
        public boolean isInProgress() {
            return state == State.BUILDING;
        }

        @Override
        public void run() throws AsynchronousExecution {
            state = State.BUILDING;
            try {
                Files.createDirectories(this.getLogFile().toPath().getParent());
                Files.createFile(this.getLogFile().toPath());
                listener = new StreamTaskListener(this.getLogFile(), StandardCharsets.UTF_8);
                listener.getLogger().println("Starting " + this);
                RunListener.fireStarted(this, listener);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            throw new AsynchronousExecution() {
                @Override
                public void interrupt(boolean forShutdown) {
                    if (cancellationTask != null) {
                        return;
                    }
                    listener.getLogger().println("Cancelling " + AsyncRun.this);
                    cancellationTask = new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        finish();
                    }, "Waiting to finish " + AsyncRun.this);
                    cancellationTask.start();
                }

                @Override
                public boolean blocksRestart() {
                    return false;
                }

                @Override
                public boolean displayCell() {
                    return false;
                }
            };
        }

        public void finish() {
            RunListener.fireCompleted(this, listener);
            listener.getLogger().println("Finished " + this);
            listener.closeQuietly();
            state = State.COMPLETED;
            RunListener.fireFinalized(this);
            Executor executor = getExecutor();
            if (executor != null) {
                AsynchronousExecution asynchronousExecution = executor.getAsynchronousExecution();
                if (asynchronousExecution != null) {
                    asynchronousExecution.completed(null);
                }
            }
        }
    }
}
