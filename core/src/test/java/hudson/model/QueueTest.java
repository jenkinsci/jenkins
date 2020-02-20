package hudson.model;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class QueueTest {

    @Mock
    StaplerResponse resp;
    @Mock
    Queue.Task task;
    @Mock
    PrintWriter writer;

    @Before
    public void setup() throws IOException {
        when(resp.getWriter()).thenReturn(writer);
    }

    @Issue("JENKINS-21311")
    @Test
    public void cancelItemOnaValidItemShouldReturnA204() throws IOException, ServletException {
        when(task.hasAbortPermission()).thenReturn(true);
        Queue queue = new Queue(LoadBalancer.CONSISTENT_HASH);
        queue.schedule(task, 6000);

        HttpResponse httpResponse = queue.doCancelItem(Queue.WaitingItem.getCurrentCounterValue());
        httpResponse.generateResponse(null, resp, null);

        verify(resp).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Issue("JENKINS-21311")
    @Test
    public void cancelItemOnANonExistingItemShouldReturnA404()  throws IOException, ServletException {
        Queue queue = new Queue(LoadBalancer.CONSISTENT_HASH);
        queue.schedule(task, 6000);

        HttpResponse httpResponse = queue.doCancelItem(Queue.WaitingItem.getCurrentCounterValue() + 1);
        httpResponse.generateResponse(null, resp, null);

        verify(resp).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Issue("JENKINS-21311")
    @Test
    public void cancelItemOnANonCancellableItemShouldReturnA422()  throws IOException, ServletException {
        when(task.hasAbortPermission()).thenReturn(false);
        Queue queue = new Queue(LoadBalancer.CONSISTENT_HASH);
        queue.schedule(task, 6000);

        HttpResponse httpResponse = queue.doCancelItem(Queue.WaitingItem.getCurrentCounterValue());
        httpResponse.generateResponse(null, resp, null);

        verify(resp).setStatus(422);
    }
}
