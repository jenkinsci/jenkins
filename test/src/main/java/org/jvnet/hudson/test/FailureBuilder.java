package org.jvnet.hudson.test;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.Builder;
import hudson.tasks.BuildStep;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Mock {@link Builder} that always cause a build to fail.
 *
 * @author Kohsuke Kawaguchi
 */
public class FailureBuilder extends Builder {
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("Simulating a failure");
        build.setResult(Result.FAILURE);
        return false;
    }

    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Builder> {
        private DescriptorImpl() {
            super(FailureBuilder.class);
        }

        public Builder newInstance(StaplerRequest req, JSONObject data) {
            throw new UnsupportedOperationException();
        }

        public String getDisplayName() {
            return "Always fail";
        }
    }

    static {
        BuildStep.BUILDERS.add(DESCRIPTOR);
    }
}
