package org.jvnet.hudson.test;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * {@link Builder} that just sleeps for the specified amount of milli-seconds.
 * 
 * @author Kohsuke Kawaguchi
 */
public class SleepBuilder extends Builder {
    public final long time;

    public SleepBuilder(long time) {
        this.time = time;
    }

    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("Sleeping "+time+"ms");
        Thread.sleep(time);
        return true;
    }

    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Builder> {
        private DescriptorImpl() {
            super(SleepBuilder.class);
        }

        public Builder newInstance(StaplerRequest req, JSONObject data) {
            throw new UnsupportedOperationException();
        }

        public String getDisplayName() {
            return "Sleep";
        }
    }

    static {
        BuildStep.BUILDERS.add(DESCRIPTOR);
    }
}
