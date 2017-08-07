package hudson.model.view.operations;

import org.kohsuke.stapler.StaplerRequest;

/**
 * Created by haswell on 8/7/17.
 */
public interface RequestDecorator {
    void decorateRequest(StaplerRequest request);
}
