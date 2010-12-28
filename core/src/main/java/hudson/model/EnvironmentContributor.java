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
import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.io.IOException;

/**
 * Contributes environment variables to builds.
 *
 * <p>
 * This extension point can be used to externally add environment variables. Aside from adding environment variables
 * of the fixed name, a typical strategy is to look for specific {@link JobProperty}s and other similar configurations
 * of {@link Job}s to compute values.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.392
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
     * @param r
     *      Build that's being performed. Never null.
     * @param envs
     *      Partially built environment variable map. Implementation of this method is expected to
     *      add additional variables here. Never null.
     * @param listener
     *      Connected to the build console. Can be used to report errors. Never null.
     */
    public abstract void buildEnvironmentFor(Run r, EnvVars envs, TaskListener listener) throws IOException, InterruptedException;

    /**
     * Returns all the registered {@link EnvironmentContributor}s.
     */
    public static ExtensionList<EnvironmentContributor> all() {
        return Hudson.getInstance().getExtensionList(EnvironmentContributor.class);
    }
}
