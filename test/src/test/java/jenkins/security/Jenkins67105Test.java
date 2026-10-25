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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Jenkins67105Test {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Issue("JENKINS-67105")
    @Test
    void arrayListMultimap() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(r.createSlave());
        p.getBuildersList().add(new GuavaBuilder(new ArrayListMultimapCallable()));
        r.buildAndAssertSuccess(p);
    }

    @Issue("JENKINS-67105")
    @Test
    void hashMultimap() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(r.createSlave());
        p.getBuildersList().add(new GuavaBuilder(new HashMultimapCallable()));
        r.buildAndAssertSuccess(p);
    }

    public static class GuavaBuilder extends Builder {
        private final MasterToSlaveCallable<?, RuntimeException> callable;

        @SuppressWarnings("checkstyle:redundantmodifier")
        public GuavaBuilder(MasterToSlaveCallable<?, RuntimeException> callable) {
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
            extends MasterToSlaveCallable<Multimap<?, ?>, RuntimeException> {
        @Override
        public Multimap<?, ?> call() throws RuntimeException {
            return ArrayListMultimap.create();
        }
    }

    private static class HashMultimapCallable
            extends MasterToSlaveCallable<Multimap<?, ?>, RuntimeException> {
        @Override
        public Multimap<?, ?> call() throws RuntimeException {
            return HashMultimap.create();
        }
    }
}
