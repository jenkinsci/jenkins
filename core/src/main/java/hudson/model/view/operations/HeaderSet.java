package hudson.model.view.operations;

import org.kohsuke.stapler.StaplerResponse;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by haswell on 8/7/17.
 */
public class HeaderSet implements ResponseDecorator {

    private final Set<Header> headers;

    public HeaderSet(Header...headers) {
        //header order might matter for some browsers
        this.headers = new LinkedHashSet<>(Arrays.asList(headers));
    }



    @Override
    public void decorateResponse(StaplerResponse response) {
        for(Header header : headers) {
            header.decorateResponse(response);
        }
    }
}
