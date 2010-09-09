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

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class WideJobProperty extends JobProperty<AbstractProject<?,?>> {
    public final int weight;

    @DataBoundConstructor
    public WideJobProperty(int weight) {
        this.weight = weight;
    }

    @Override
    public List<SubTask> getSubTasks() {
        List<SubTask> r = new ArrayList<SubTask>();
        for (int i=1; i< weight; i++)
            r.add(new AbstractSubTask() {
                private final AbstractSubTask outerThis = this;
                public Executable createExecutable() throws IOException {
                    return new Executable() {
                        public SubTask getParent() {
                            return outerThis;
                        }

                        public void run() {
                            // nothing. we just waste time
                        }
                    };
                }

                @Override
                public Object getSameNodeConstraint() {
                    // must occupy the same node as the project itself
                    return getProject();
                }

                @Override
                public long getEstimatedDuration() {
                    return getProject().getEstimatedDuration();
                }

                public Task getOwnerTask() {
                    return getProject();
                }

                public String getDisplayName() {
                    return "place holder for "+getProject().getDisplayName();
                }

                private AbstractProject<?, ?> getProject() {
                    return WideJobProperty.this.owner;
                }
            });
        return r;
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "This project occupies multiple executors";
        }
    }
}
