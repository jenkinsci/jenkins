package hudson.model;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueueTest {

    @Mock
    private StaplerResponse2 resp;
    @Mock
    private Queue.Task task;
    @Mock
    private PrintWriter writer;

    @BeforeEach
    void setup() throws IOException {
        when(resp.getWriter()).thenReturn(writer);
    }

    @Issue("JENKINS-21311")
    @Test
    void cancelItemOnaValidItemShouldReturnA204() throws IOException, ServletException {
        when(task.hasAbortPermission()).thenReturn(true);
        Queue queue = new Queue(LoadBalancer.CONSISTENT_HASH);
        long id = queue.schedule(task, 6000).getId();

        HttpResponse httpResponse = queue.doCancelItem(id);
        httpResponse.generateResponse(null, resp, null);

        verify(resp).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Issue("JENKINS-21311")
    @Test
    void cancelItemOnANonExistingItemShouldReturnA404()  throws IOException, ServletException {
        Queue queue = new Queue(LoadBalancer.CONSISTENT_HASH);
        long id = queue.schedule(task, 6000).getId();

        HttpResponse httpResponse = queue.doCancelItem(id + 1);
        httpResponse.generateResponse(null, resp, null);

        verify(resp).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Issue("JENKINS-21311")
    @Test
    void cancelItemOnANonCancellableItemShouldReturnA422()  throws IOException, ServletException {
        when(task.hasAbortPermission()).thenReturn(false);
        Queue queue = new Queue(LoadBalancer.CONSISTENT_HASH);
        long id = queue.schedule(task, 6000).getId();

        HttpResponse httpResponse = queue.doCancelItem(id);
        httpResponse.generateResponse(null, resp, null);

        verify(resp).setStatus(422);
    }
}
