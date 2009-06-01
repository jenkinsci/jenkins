package org.jvnet.hudson.test;

import hudson.tasks.Builder;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Descriptor;
import hudson.Launcher;
import hudson.Extension;

import java.io.IOException;

import org.kohsuke.stapler.StaplerRequest;
import net.sf.json.JSONObject;

/**
 * Forces the build result to be some pre-configured value.
 *
 * @author Kohsuke Kawaguchi
 */
public class MockBuilder extends Builder {
    public final Result result;

    public MockBuilder(Result result) {
        this.result = result;
    }

    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("Simulating a specific result code "+result);
        build.setResult(result);
        return false;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Builder> {
        public Builder newInstance(StaplerRequest req, JSONObject data) {
            throw new UnsupportedOperationException();
        }

        public String getDisplayName() {
            return "Force the build result";
        }
    }
}

