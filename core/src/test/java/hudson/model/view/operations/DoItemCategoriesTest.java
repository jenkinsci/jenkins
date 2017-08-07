package hudson.model.view.operations;

import hudson.model.View;
import org.junit.Test;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.util.Collections;

import static hudson.model.view.operations.HeadersTest.mockResponse;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

/**
 * Created by haswell on 8/7/17.
 */
public class DoItemCategoriesTest {

    @Test
    public void ensureViewDoItemCategoriesWorks() {
        DoItemCategories doItemCategories =
                spy(new DoItemCategories(mock(View.class)));


        doReturn(Collections.emptyList())
                .when(doItemCategories)
                .listDescriptors();




        final StaplerRequest request = mock(StaplerRequest.class);
        final StaplerResponse response = mockResponse();

        doItemCategories.invoke(request, response, "nothere");

        String header = response.getHeader(Headers.PRAGMA);
        assertThat(header, is(Headers.Values.NO_CACHE));
    }

}