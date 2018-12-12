/*
 * The MIT License
 *
 * Copyright (c) 2015, Daniel Weber
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
package hudson.util;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.util.ProcessTreeRemoting.IOSProcess;

import java.util.Collections;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.util.JenkinsJVM;

/**
 * Allows extensions to veto killing processes. If at least one extension vetoes
 * the killing of a process, it will not be killed. This can be useful to keep
 * daemon processes alive. An example is mspdbsrv.exe used by Microsoft
 * compilers.
 * 
 * See JENKINS-9104
 * 
 * @since 1.619
 * 
 * @author <a href="mailto:daniel.weber.dev@gmail.com">Daniel Weber</a>
 */
public abstract class ProcessKillingVeto implements ExtensionPoint {

    /**
     * Describes the cause for a process killing veto.
     */
    public static class VetoCause {
        private final String message;

        /**
         * @param message A string describing the reason for the veto
         */
        public VetoCause(@Nonnull String message) {
            this.message = message;
        }

        /**
         * @return A string describing the reason for the veto.
         */
        public @Nonnull String getMessage() {
            return message;
        }
    }

    /**
     * @return All ProcessKillingVeto extensions currently registered. An empty
     *         list if Jenkins is not available, never null.
     */
    public static List<ProcessKillingVeto> all() {
        if (JenkinsJVM.isJenkinsJVM()) {
            return _all();
        }
        return Collections.emptyList();
    }

    /**
     * As classloading is lazy, the classes referenced in this method will not be resolved
     * until the first time the method is invoked, so we use this method to guard access to Jenkins JVM only classes.
     *
     * @return All ProcessKillingVeto extensions currently registered.
     */
    private static List<ProcessKillingVeto> _all() {
        return ExtensionList.lookup(ProcessKillingVeto.class);
    }

    /**
     * Ask the extension whether it vetoes killing of the given process
     * 
     * @param p The process that is about to be killed
     * @return a {@link VetoCause} if the process should <em>not</em> be killed,
     *         null else.
     */
    @CheckForNull
    public abstract VetoCause vetoProcessKilling(@Nonnull IOSProcess p);
}
