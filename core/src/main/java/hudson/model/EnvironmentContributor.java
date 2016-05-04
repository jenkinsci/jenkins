/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
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
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.scm.SCM;

import java.io.IOException;
import javax.annotation.Nonnull;

/**
 * Contributes environment variables to builds.
 *
 * <p>
 * This extension point can be used to externally add environment variables. Aside from adding environment variables
 * of the fixed name, a typical strategy is to look for specific {@link JobProperty}s and other similar configurations
 * of {@link Job}s to compute values.
 *
 * <h2>Views</h2>
 * <h4>buildEnv.groovy/.jelly</h4>
 * <p>
 * When Jenkins displays the help page listing all the environment variables available for a build, it does
 * so by combining all the {@code buildEnv} views from this extension point. This view should use the &lt;t:buildEnvVar> tag
 * to render a variable.
 *
 * <p>
 * In this view, {@code it} points to {@link EnvironmentContributor} and {@code job} points to {@link Job} for which
 * the help is being rendered.
 *
 * <p>
 * Jenkins provides other extension points (such as {@link SCM}) to contribute environment variables to builds,
 * and for those plugins, Jenkins also looks for {@code /buildEnv.groovy} and aggregates them.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.392
 * @see BuildVariableContributor
 */
public abstract class EnvironmentContributor implements ExtensionPoint {
    /**
     * Contributes environment variables used for a build.
     *
     * <p>
     * This method can be called repeatedly for the same {@link Run}, thus
     * the computation of this method needs to be efficient. If you have a time-consuming
     * computation, one strategy is to take the hit once and then add the result as {@link InvisibleAction}
     * to {@link Run}, then reuse those values later on.
     *
     * <p>
     * This method gets invoked concurrently for multiple {@link Run}s that are being built at the same time,
     * so it must be concurrent-safe.
     *
     * <p>
     * When building environment variables for a build, Jenkins will also invoke
     * {@link #buildEnvironmentFor(Job, EnvVars, TaskListener)}. This method only needs to add
     * variables that are scoped to builds.
     *
     * @param r
     *      Build that's being performed.
     * @param envs
     *      Partially built environment variable map. Implementation of this method is expected to
     *      add additional variables here.
     * @param listener
     *      Connected to the build console. Can be used to report errors.
     */
    public void buildEnvironmentFor(@Nonnull Run r, @Nonnull EnvVars envs, @Nonnull TaskListener listener) throws IOException, InterruptedException {}

    /**
     * Contributes environment variables used for a job.
     *
     * <p>
     * This method can be called repeatedly for the same {@link Job}, thus
     * the computation of this method needs to be efficient.
     *
     * <p>
     * This method gets invoked concurrently for multiple {@link Job}s,
     * so it must be concurrent-safe.
     *
     * @param j
     *      Job for which some activities are launched.
     * @param envs
     *      Partially built environment variable map. Implementation of this method is expected to
     *      add additional variables here.
     * @param listener
     *      Connected to the build console. Can be used to report errors.
     * @since 1.527
     */
    public void buildEnvironmentFor(@Nonnull Job j, @Nonnull EnvVars envs, @Nonnull TaskListener listener) throws IOException, InterruptedException {}

    /**
     * Returns all the registered {@link EnvironmentContributor}s.
     */
    public static ExtensionList<EnvironmentContributor> all() {
        return ExtensionList.lookup(EnvironmentContributor.class);
    }

    /**
     * Serves the combined list of environment variables available from this plugin.
     *
     * Served from "/env-vars.html"
     */
    @Extension
    public static class EnvVarsHtml implements RootAction {
        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return null;
        }

        public String getUrlName() {
            return "env-vars.html";
        }
    }
}
