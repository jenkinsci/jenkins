package hudson.model.view.operations;

import org.junit.Test;
import org.kohsuke.stapler.StaplerResponse;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Created by haswell on 8/7/17.
 */
public class HeaderTest {


    @Test
    public void ensureHeaderDecoratesHeaderWithAtLeastOneObjectCorrectly() {

        final Header header = new Header("hello", "world");

        StaplerResponse response = HeadersTest.mockResponse();
        header.decorateResponse(response);
        assertThat(response.getHeader("hello"), is("world"));

    }

}