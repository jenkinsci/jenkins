package hudson.model.view.operations;

import org.kohsuke.stapler.StaplerResponse;

/**
 * Created by haswell on 8/7/17.
 */
public interface ResponseDecorator {
    void decorateResponse(StaplerResponse response);
}
