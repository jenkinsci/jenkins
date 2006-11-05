package hudson.model;

import java.io.Serializable;

/**
 * Contributes an item to the task list.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Action extends Serializable, ModelObject {
    /**
     * Gets the file name of the icon (relative to /images/24x24)
     */
    String getIconFileName();

    /**
     * Gets the string to be displayed.
     *
     * The convention is to capitalize the first letter of each word,
     * such as "Test Result". 
     */
    String getDisplayName();

    /**
     * Gets the URL path name.
     */
    String getUrlName();
}
