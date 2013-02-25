/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.maven;

/**
 * A part of {@link MavenBuildProxy} that's used internally
 * for aggregated build. Fired and consumed internally and
 * not exposed to plugins.
 *
 * @author Kohsuke Kawaguchi
 */
public interface MavenBuildProxy2 extends MavenBuildProxy {
    /**
     * Notifies that the build has entered a module.
     *
     * Jenkins has a somewhat simplified view of the sequence of events in a Maven build.
     * Namely, a Maven build can be building a particular Maven module, and start and end marks that boundary.
     * Note that Maven can be in a state that it's not building any module, for example when executing an aggregator mojo,
     * and start/end cannot be nested (which right now means a forked lifecycle doesn't result in the event firing.)
     */
    void start();

    /**
     * Notifies that the build has left a module.
     */
    void end();

    /**
     * Maven produces additional error message after the module build is done.
     * So to catch those messages, invoke this method on the last module that was built
     * after all the Maven processing is done, to append last messages to the console
     * output of the module.
     */
    void appendLastLog();

    /**
     * Filter for {@link MavenBuildProxy2}.
     *
     * Meant to be useful as the base class for other filters.
     */
    /*package*/ abstract class Filter<CORE extends MavenBuildProxy2> extends MavenBuildProxy.Filter<CORE> implements MavenBuildProxy2 {
        private static final long serialVersionUID = -1208416744028858844L;

        protected Filter(CORE core) {
            super(core);
        }

        public void start() {
            core.start();
        }

        public void end() {
            core.end();
        }

        public void appendLastLog() {
            core.appendLastLog();
        }
    }
}
