package jenkins.model;

import hudson.model.Saveable;
import java.io.IOException;

/**
 * Object whose state can be loaded from disk. In general, also implements {@link Saveable}.
 *
 * @since 2.428
 */
public interface Loadable {

    /**
     * Loads the state of this object from disk.
     *
     * @throws IOException The state could not be loaded.
     */
    void load() throws IOException;
}
