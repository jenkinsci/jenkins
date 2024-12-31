package jenkins.security;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class Jenkins67105Test {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-67105")
    @Test
    public void arrayListMultimap() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(r.createAgent());
        p.getBuildersList().add(new GuavaBuilder(new ArrayListMultimapCallable()));
        r.buildAndAssertSuccess(p);
    }

    @Issue("JENKINS-67105")
    @Test
    public void hashMultimap() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(r.createAgent());
        p.getBuildersList().add(new GuavaBuilder(new HashMultimapCallable()));
        r.buildAndAssertSuccess(p);
    }

    public static class GuavaBuilder extends Builder {
        private final MasterToAgentCallable<?, RuntimeException> callable;

        public GuavaBuilder(MasterToAgentCallable<?, RuntimeException> callable) {
            this.callable = callable;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            listener.getLogger().println("received " + launcher.getChannel().call(callable));
            return true;
        }
    }

    private static class ArrayListMultimapCallable
            extends MasterToAgentCallable<Multimap<?, ?>, RuntimeException> {
        @Override
        public Multimap<?, ?> call() throws RuntimeException {
            return ArrayListMultimap.create();
        }
    }

    private static class HashMultimapCallable
            extends MasterToAgentCallable<Multimap<?, ?>, RuntimeException> {
        @Override
        public Multimap<?, ?> call() throws RuntimeException {
            return HashMultimap.create();
        }
    }
}
