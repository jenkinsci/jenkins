package hudson.model;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class QueueTest {

    @Mock
    StaplerResponse2 resp;
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
        long id = queue.schedule(task, 6000).getId();

        HttpResponse httpResponse = queue.doCancelItem(id);
        httpResponse.generateResponse(null, resp, null);

        verify(resp).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Issue("JENKINS-21311")
    @Test
    public void cancelItemOnANonExistingItemShouldReturnA404()  throws IOException, ServletException {
        Queue queue = new Queue(LoadBalancer.CONSISTENT_HASH);
        long id = queue.schedule(task, 6000).getId();

        HttpResponse httpResponse = queue.doCancelItem(id + 1);
        httpResponse.generateResponse(null, resp, null);

        verify(resp).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Issue("JENKINS-21311")
    @Test
    public void cancelItemOnANonCancellableItemShouldReturnA422()  throws IOException, ServletException {
        when(task.hasAbortPermission()).thenReturn(false);
        Queue queue = new Queue(LoadBalancer.CONSISTENT_HASH);
        long id = queue.schedule(task, 6000).getId();

        HttpResponse httpResponse = queue.doCancelItem(id);
        httpResponse.generateResponse(null, resp, null);

        verify(resp).setStatus(422);
    }
}
