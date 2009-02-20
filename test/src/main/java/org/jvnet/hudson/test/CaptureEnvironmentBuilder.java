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
import java.util.Map;

/**
 * Mock {@link Builder} that always cause a build to fail.
 *
 * @author Kohsuke Kawaguchi
 */
public class CaptureEnvironmentBuilder extends Builder {
	
    private Map<String, String> envVars;

	public Map<String, String> getEnvVars() {
		return envVars;
	}

	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
    	envVars = build.getEnvVars();
        return true;
    }

    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Builder> {
        public Builder newInstance(StaplerRequest req, JSONObject data) {
            throw new UnsupportedOperationException();
        }

        public String getDisplayName() {
            return "Capture Environment Variables";
        }
    }

    static {
        BuildStep.BUILDERS.add(DESCRIPTOR);
    }
}
