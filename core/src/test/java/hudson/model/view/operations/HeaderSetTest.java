package hudson.model.view.operations;

import org.junit.Test;
import org.kohsuke.stapler.StaplerResponse;

import static hudson.model.view.operations.HeadersTest.mockResponse;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


/**
 * Created by haswell on 8/7/17.
 */
public class HeaderSetTest {


    @Test
    public void ensureHeaderSetSetsCorrectHeadersForSingleHeaderValue() {
        final StaplerResponse response = mockResponse();

        String key = "header-key";
        String value = "header-value";

        Header header = new Header(key, value);
        header.decorateResponse(response);
        String actual = response.getHeader(key);
        assertThat(actual, is(value));
    }


    @Test
    public void ensureHeaderSetSetsCorrectHeadersForMultipleValues() {
        final StaplerResponse response = mockResponse();
        Headers.NO_CACHE.decorateResponse(response);

        assertThat(
                response.getHeader(Headers.CACHE_CONTROL),
                is("no cache, no store, must revalidate")
        );

        assertThat(
                response.getHeader(Headers.PRAGMA),
                is(Headers.Values.NO_CACHE)
        );

    }




}