/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package org.jvnet.hudson.test;

import groovy.lang.Closure;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * {@link JenkinsRule} variant with special options for tests written in Groovy.
 * @since 1.535
 */
public class GroovyJenkinsRule extends JenkinsRule {

    /**
     * Executes the given closure on the server, in the context of an HTTP request.
     * This is useful for testing some methods that require {@link StaplerRequest} and {@link StaplerResponse}.
     *
     * <p>
     * The closure will get the request and response as parameters.
     */
    public Object executeOnServer(final Closure c) throws Exception {
        return executeOnServer(new Callable<Object>() {
            @Override public Object call() throws Exception {
                return c.call();
            }
        });
    }

    /**
     * Wraps a closure as a {@link Builder}.
     */
    public Builder builder(final Closure c) {
        return new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                Object r = c.call(new Object[] {build, launcher, listener});
                if (r instanceof Boolean) {
                    return (Boolean) r;
                }
                return true;
            }
        };
    }

}
