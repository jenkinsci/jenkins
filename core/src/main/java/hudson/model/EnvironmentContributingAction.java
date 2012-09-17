/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.model;

import hudson.EnvVars;
import hudson.model.Queue.Task;
import hudson.tasks.Builder;
import hudson.tasks.BuildWrapper;

/**
 * {@link Action} that contributes environment variables during a build.
 *
 * <p>
 * For example, your {@link Builder} can add an {@link EnvironmentContributingAction} so that
 * the rest of the builders or publishers see some behavior changes.
 *
 * Another use case is for you to {@linkplain Queue#schedule(Task, int, Action...) submit a job} with
 * {@link EnvironmentContributingAction}s.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.318
 * @see AbstractBuild#getEnvironment(TaskListener)
 * @see BuildWrapper
 */
public interface EnvironmentContributingAction extends Action {
    /**
     * Called by {@link AbstractBuild} to allow plugins to contribute environment variables.
     *
     * @param build
     *      The calling build. Never null.
     * @param env
     *      Environment variables should be added to this map.
     */
    void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env);
}
