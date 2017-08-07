package hudson.model.view.operations;

import org.kohsuke.stapler.StaplerResponse;

/**
 * Created by haswell on 8/7/17.
 */
public class Header implements ResponseDecorator {


    private final String key;

    private final String value;

    public Header(String key, Object... values) {
        this.key = key;
        this.value = headerValue(values);
    }


    @Override
    public void decorateResponse(StaplerResponse response) {
        response.addHeader(key, value);
    }



    private String headerValue(Object[] values) {
        final StringBuilder b = new StringBuilder();

        for(int i = 0; i < values.length - 1; i++) {
            b.append(values[i]);
            b.append(", ");
        }

        b.append(values[values.length - 1]);
        return b.toString();
    }

}
