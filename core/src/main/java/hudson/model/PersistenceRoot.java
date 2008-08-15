package hudson.model;

import java.io.File;

/**
 * Root object of a persisted object tree
 * that gets its own file system directory.
 *
 * @author Kohsuke Kawaguchi
 */
public interface PersistenceRoot extends Saveable {
    /**
     * Gets the root directory on the file system that this
     * {@link Item} can use freely fore storing the configuration data.
     *
     * <p>
     * This parameter is given by the {@link ItemGroup} when
     * {@link Item} is loaded from memory.
     */
    File getRootDir();
}
