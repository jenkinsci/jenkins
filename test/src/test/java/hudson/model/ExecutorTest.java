package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.Functions;
import hudson.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.util.OneShotEvent;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.model.CauseOfInterruption.UserInterruption;
import jenkins.model.InterruptedBuildAction;
import jenkins.model.Jenkins;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestBuilder;

public class ExecutorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    @Issue("JENKINS-4756")
    public void whenAnExecutorDiesHardANewExecutorTakesItsPlace() throws Exception {
        j.jenkins.setNumExecutors(1);

        Computer c = j.jenkins.toComputer();
        Executor e = getExecutorByNumber(c, 0);

        j.jenkins.getQueue().schedule(new QueueTest.TestTask(new AtomicInteger()) {
            @Override
            public Queue.Executable createExecutable() {
                throw new IllegalStateException("oops");
            }
        }, 0);
        while (e.isActive()) {
            Thread.sleep(10);
        }

        waitUntilExecutorSizeIs(c, 1);

        assertNotNull(getExecutorByNumber(c, 0));
    }

    private void waitUntilExecutorSizeIs(Computer c, int executorCollectionSize) throws InterruptedException {
        int timeOut = 10;
        while (c.getExecutors().size() != executorCollectionSize) {
            Thread.sleep(10);
            if (timeOut-- == 0) fail("executor collection size was not " + executorCollectionSize);
        }
    }

    private Executor getExecutorByNumber(Computer c, int executorNumber) {
        for (Executor executor : c.getExecutors()) {
            if (executor.getNumber() == executorNumber) {
                return executor;
            }
        }
        return null;
    }

    /**
     * Makes sure that the cause of interruption is properly recorded.
     */
    @Test
    public void abortCause() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        FreeStyleBuild b = startBlockingBuild(p);

        User johnny = User.getOrCreateByIdOrFullName("Johnny");
        p.getLastBuild().getExecutor().interrupt(Result.FAILURE,
                new UserInterruption(johnny),   // test the merge semantics
                new UserInterruption(johnny));

        // make sure this information is recorded
        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        InterruptedBuildAction iba = b.getAction(InterruptedBuildAction.class);
        assertEquals(1, iba.getCauses().size());
        assertEquals(((UserInterruption) iba.getCauses().get(0)).getUser(), johnny);

        // make sure it shows up in the log
        j.assertLogContains(johnny.getId(), b);
    }

    @Test
    public void disconnectCause() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(slave);

        FreeStyleBuild b = startBlockingBuild(p);
        User johnny = User.getOrCreateByIdOrFullName("Johnny");

        p.getLastBuild().getBuiltOn().toComputer().disconnect(
                new OfflineCause.UserCause(johnny, "Taking offline to break your build")
        );

        j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
        j.assertLogContains("Finished: FAILURE", b);
        j.assertLogContains("Build step 'TestBuilder' marked build as failure", b);
        j.assertLogContains("Agent went offline during the build", b);
        // The test reports a legacy code cause in up to 10% of jobs on ci.jenkins.io.
        // Accept either cause and assert the expected message for each type of cause.
        if (p.getLastBuild().getCause(Cause.class) instanceof Cause.UserIdCause) {
            j.assertLogContains("Disconnected by Johnny : Taking offline to break your build", b);
        } else {
            assertThat(p.getLastBuild().getCause(Cause.class), instanceOf(Cause.LegacyCodeCause.class));
            j.assertLogContains(Cause.LegacyCodeCause.getShortDescription(), b);
        }
    }

    @Issue("SECURITY-611")
    @Test
    public void apiPermissions() throws Exception {
        DumbSlave slave = new DumbSlave("slave", j.jenkins.getRootDir().getAbsolutePath(), j.createComputerLauncher(null));
        slave.setNumExecutors(2);
        j.jenkins.addNode(slave);
        FreeStyleProject publicProject = j.createFreeStyleProject("public-project");
        publicProject.setAssignedNode(slave);
        FreeStyleProject secretProject = j.createFreeStyleProject("secret-project");
        secretProject.setAssignedNode(slave);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.READ).everywhere().toEveryone().
            grant(Item.READ).onItems(publicProject).toEveryone().
            grant(Item.READ).onItems(secretProject).to("has-security-clearance"));

        FreeStyleBuild b1 = startBlockingBuild(publicProject);
        FreeStyleBuild b2 = startBlockingBuild(secretProject);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials("has-security-clearance");
        String api = wc.goTo(slave.toComputer().getUrl() + "api/json?pretty&depth=1", null).getWebResponse().getContentAsString();
        System.out.println(api);
        assertThat(api, allOf(containsString("public-project"), containsString("secret-project")));

        wc = j.createWebClient();
        wc.withBasicCredentials("regular-joe");
        api = wc.goTo(slave.toComputer().getUrl() + "api/json?pretty&depth=1", null).getWebResponse().getContentAsString();
        System.out.println(api);
        assertThat(api, allOf(containsString("public-project"), not(containsString("secret-project"))));

        b1.doStop();
        b2.doStop();
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b1));
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b2));
    }

    @Test
    @Issue("SECURITY-2120")
    public void disconnectCause_WithoutTrace() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(slave);

        FreeStyleBuild b = startBlockingBuild(p);

        String message = "It went away";
        p.getLastBuild().getBuiltOn().toComputer().disconnect(
                new OfflineCause.ChannelTermination(new RuntimeException(message))
        );

        OfflineCause offlineCause = p.getLastBuild().getBuiltOn().toComputer().getOfflineCause();
        assertThat(offlineCause.toString(), not(containsString(message)));

        b.doStop();
        j.waitForCompletion(b);
    }

    /**
     * Start a project with an infinite build step
     *
     * @param project {@link FreeStyleProject} to start
     * @return the started build (the caller should wait for its completion)
     * @throws Exception if somethink wrong happened
     */
    public static FreeStyleBuild startBlockingBuild(FreeStyleProject project) throws Exception {
        final OneShotEvent e = new OneShotEvent();

        project.getBuildersList().add(new BlockingBuilder(e));

        FreeStyleBuild b = project.scheduleBuild2(0).waitForStart();
        e.block();  // wait until we are safe to interrupt
        assertTrue(b.isBuilding());

        return b;
    }

    private static final class BlockingBuilder extends TestBuilder {
        private final OneShotEvent e;

        private BlockingBuilder(OneShotEvent e) {
            this.e = e;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            VirtualChannel channel = launcher.getChannel();
            Node node = build.getBuiltOn();
            channel.call(node.getClockDifferenceCallable()); // warm up class loading

            e.signal(); // we are safe to be interrupted
            for (;;) {
                // Keep using the channel
                try {
                    channel.call(node.getClockDifferenceCallable());
                } catch (IOException x) {
                    if (x.getMessage().contains("RemoteClassLoader.ClassLoaderProxy")) {
                        Functions.printStackTrace(x, listener.error("TODO unreproducible error from MultiClassLoaderSerializer.Input.readClassLoader"));
                    } else {
                        throw x;
                    }
                }
                Thread.sleep(100);
            }
        }
    }
}
