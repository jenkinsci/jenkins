package hudson.slaves;

import static org.junit.Assert.fail;

import hudson.model.Slave;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class NodeParallelTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public InboundAgentRule inboundAgents = new InboundAgentRule();

    private static final Logger LOGGER = Logger.getLogger(NodeParallelTest.class.getName());

    private final AtomicInteger count = new AtomicInteger();

    @Test
    @Issue("JENKINS-53401")
    public void createNodesWithParallelThreads() throws InterruptedException, ExecutionException {
        int n = 50;
        List<Callable<Void>> tasks = Collections.nCopies(n, () -> {
            try {
                int i = count.incrementAndGet();
                LOGGER.log(Level.INFO, "Creating slave " + i);
                // JenkinsRule sync on Jenkins singleton, so this doesn't work
                // r.createSlave();
                Slave agent = inboundAgents.createAgent(r, InboundAgentRule.Options.newBuilder().name("agent-" + i).skipStart().build());
                agent.setNodeProperties(List.of(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("foo", "" + i))));
                return null;
            } catch (Exception e1) {
                throw new RuntimeException(e1);
            }
        });
        ExecutorService executorService = Executors.newFixedThreadPool(n);
        List<Future<Void>> futures = executorService.invokeAll(tasks);
        List<Void> resultList = new ArrayList<>(futures.size());
        // Check for exceptions
        try {
            for (Future<Void> future : futures) {
                // Throws an exception if an exception was thrown by the task.
                resultList.add(future.get());
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
