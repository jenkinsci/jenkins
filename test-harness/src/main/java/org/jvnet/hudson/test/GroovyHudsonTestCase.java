package org.jvnet.hudson.test;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import groovy.lang.Closure;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;
import hudson.Launcher;

import java.util.concurrent.Callable;
import java.io.IOException;

/**
 * {@link HudsonTestCase} with more convenience methods for Groovy.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated Use {@link GroovyJenkinsRule} instead.
 */
@Deprecated
public abstract class GroovyHudsonTestCase extends HudsonTestCase {
    /**
     * Executes the given closure on the server, in the context of an HTTP request.
     * This is useful for testing some methods that require {@link StaplerRequest} and {@link StaplerResponse}.
     *
     * <p>
     * The closure will get the request and response as parameters.
     */
    public Object executeOnServer(final Closure c) throws Exception {
        return executeOnServer(new Callable<Object>() {
            public Object call() throws Exception {
                return c.call();
            }
        });
    }

    /**
     * Wraps a closure as a {@link Builder}.
     */
    public Builder builder(final Closure c) {
        return new TestBuilder() {
            public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                Object r = c.call(new Object[]{build,launcher,listener});
                if (r instanceof Boolean)   return (Boolean)r;
                return true;
            }
        };
    }
}
