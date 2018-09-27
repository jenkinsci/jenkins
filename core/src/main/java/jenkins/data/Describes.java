package jenkins.data;

import hudson.model.Describable;

/**
 * Put on {@link DataModel} subtype to indicate which {@link Describable} object the model is describing.
 *
 * @author Kohsuke Kawaguchi
 */
public @interface Describes {
    /**
     * What class this binds?
     */
    Class value();
}
