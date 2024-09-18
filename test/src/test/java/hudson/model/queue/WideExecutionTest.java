/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

package hudson.model.queue;

import static org.junit.Assert.assertEquals;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Kohsuke Kawaguchi
 */
public class WideExecutionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @TestExtension
    public static class Contributor extends SubTaskContributor {
        @Override
        public Collection<? extends SubTask> forProject(final AbstractProject<?, ?> p) {
            return Collections.singleton(new SubTask() {
                private final SubTask outer = this;
                @Override
                public Executable createExecutable() {
                    return new Executable() {
                        @Override
                        public SubTask getParent() {
                            return outer;
                        }

                        @Override
                        public void run() {
                            WorkUnitContext wuc = Executor.currentExecutor().getCurrentWorkUnit().context;
                            AbstractBuild b = (AbstractBuild) wuc.getPrimaryWorkUnit().getExecutable();
                            try {
                                b.setDescription("I was here");
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }

                        @Override
                        public long getEstimatedDuration() {
                            return 0;
                        }
                    };
                }

                @Override
                public Task getOwnerTask() {
                    return p;
                }

                @Override
                public String getDisplayName() {
                    return "Company of " + p.getDisplayName();
                }

                @Override
                public String getUrl() {
                    return p.getUrl() + "company/";
                }
            });
        }
    }

    @Test
    public void run() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        assertEquals("I was here", b.getDescription());
    }
}
