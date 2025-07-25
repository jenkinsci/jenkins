package jenkins.model;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An object that can be addressed via a URL in Jenkins.
 */
public interface Addressable {

    /**
     * @return the URL where to reach specifically this object, relative to Jenkins URL.
     * <p>
     * Can be empty, otherwise it must not start with leading '/' and must end with '/'.
     */
    @NonNull
    String getUrl();
}
