package hudson.model.view.operations;

import org.kohsuke.stapler.StaplerResponse;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Created by haswell on 8/7/17.
 */
public class HeadersTest {


    public static StaplerResponse mockResponse() {

        final StaplerResponse response = Mockito.mock(StaplerResponse.class);

        final Map<String, String> mockHeaders = new HashMap<>();

        configureAddHeader(response, mockHeaders);

        configureResponse(response, mockHeaders);
        return response;
    }

    private static void configureAddHeader(StaplerResponse response, Map<String, String> mockHeaders) {
        when(response.getHeader(anyString())).then(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocationOnMock) throws Throwable {
                final String arg = (String) invocationOnMock.getArguments()[0];
                return mockHeaders.get(arg);
            }
        });
    }

    private static void configureResponse(StaplerResponse response, Map<String, String> mockHeaders) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                String key = (String) invocationOnMock.getArguments()[0];
                String value = (String) invocationOnMock.getArguments()[1];
                mockHeaders.put(key, value);
                return null;
            }
        }).when(response).addHeader(anyString(), anyString());
    }


}
