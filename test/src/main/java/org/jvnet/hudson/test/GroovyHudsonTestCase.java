package org.jvnet.hudson.test;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.Stapler;
import groovy.lang.Closure;
import hudson.model.RootAction;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;
import hudson.Launcher;

import java.util.UUID;
import java.io.IOException;

/**
 * {@link HudsonTestCase} with more convenience methods for Groovy.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class GroovyHudsonTestCase extends HudsonTestCase {
    /**
     * Executes the given closure on the server, in the context of an HTTP request.
     * This is useful for testing some methods that require {@link StaplerRequest} and {@link StaplerResponse}.
     *
     * <p>
     * The closure will get the request and response as parameters.
     */
    public Object executeOnServer(final Closure c) throws Throwable {
        final Throwable[] t = new Throwable[1];
        final Object[] r = new Object[1];

        ClosureExecuterAction cea = hudson.getExtensionList(RootAction.class).get(ClosureExecuterAction.class);
        UUID id = UUID.randomUUID();
        cea.add(id,new Runnable() {
            public void run() {
                try {
                    r[0] = c.call();
                } catch (Throwable e) {
                    t[0] = e;
                }
            }
        });
        createWebClient().goTo("closures/?uuid="+id);

        if (t[0]!=null)
            throw t[0];
        return r[0];
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
