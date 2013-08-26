/*
 * The MIT License
 *
 * Copyright (c) 2011, Christian Wolfgang
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
package hudson.matrix.listeners;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.Action;
import jenkins.model.Jenkins;
import hudson.model.Queue;
import hudson.model.Queue.Task;

import java.util.List;

/**
 * Controls which subset of {@link MatrixRun}s to rebuild.
 *
 * <p>
 * Plugins can implement this extension point to filter out the subset of matrix project to build.
 * Most typically, such a plugin would add a custom {@link Action} to a build when it goes to the queue
 * ({@link Queue#schedule2(Task, int, List)}, then access this from {@link MatrixBuild} to drive
 * the filtering logic.
 *
 * <p>
 * See the matrix reloaded plugin for an example.
 *
 * @author Christian Wolfgang
 * @since 1.413
 */
public abstract class MatrixBuildListener implements ExtensionPoint {
	/**
	 * Determine whether to build a given configuration or not
     *
	 * @param b
     *      Never null. The umbrella build.
	 * @param c
     *      The configuration whose build is being considered. If any of the {@link MatrixBuildListener}
     *      returns false, then the build for this configuration is skipped, and the previous build
     *      of this configuration will be taken as the default result.
	 * @return
     *      True to let the build happen, false to skip it.
	 */
	public abstract boolean doBuildConfiguration(MatrixBuild b, MatrixConfiguration c);

	public static boolean buildConfiguration(MatrixBuild b, MatrixConfiguration c) {
		for (MatrixBuildListener l : all()) {
			if(!l.doBuildConfiguration(b, c)) {
				return false;
			}
		}
		
		return true;
	}

	/**
	 * Returns all the registered {@link MatrixBuildListener} descriptors.
	 */
	public static ExtensionList<MatrixBuildListener> all() {
		return Jenkins.getInstance().getExtensionList(MatrixBuildListener.class);
	}
}
