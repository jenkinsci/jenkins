package hudson.model;

import hudson.BulkChange;

import java.io.IOException;

/**
 * Object whose state is persisted to XML.
 *
 * @author Kohsuke Kawaguchi
 * @see BulkChange
 * @since 1.249
 */
public interface Saveable {
    /**
     * Persists the state of this object into XML.
     *
     * <p>
     * For making a bulk change efficiently, see {@link BulkChange}.
     *
     * @throws IOException
     *      if the persistence failed.
     */
    void save() throws IOException;
}
