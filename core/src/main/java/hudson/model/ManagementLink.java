package hudson.model;

import hudson.ExtensionPoint;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Extension point to add icon to <tt>http://server/hudson/manage</tt> page.
 *
 * <p>
 * This is a place for exposing features that are only meant for system admins
 * (whereas features that are meant for Hudson users at large should probably
 * be added to {@link Hudson#getActions()}.) 
 *
 * <p>
 * To register a new instance, the typical code looks like this:
 * <pre>
 * ManagementLink.LIST.add(new MyManagementLinkImpl());
 * </pre>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.194
 */
public abstract class ManagementLink implements ExtensionPoint, Action {

    /**
     * Mostly works like {@link Action#getIconFileName()}, except that
     * the expected icon size is 48x48, not 24x24. So if you give
     * just a file name, "/images/48x48" will be assumed.
     *
     * @return
     *      As a special case, return null to exclude this object from the management link.
     *      This is useful for defining {@link ManagementLink} that only shows up under
     *      certain circumstances.
     */
    public abstract String getIconFileName();

    /**
     * Returns a short description of what this link does. This text
     * is the one that's displayed in grey. This can include HTML,
     * although the use of block tags is highly discouraged.
     *
     * Optional.
     */
    public String getDescription() {
        return "";
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * In case of {@link ManagementLink}, this value is put straight into the href attribute,
     * so relative paths are interpreted against the root {@link Hudson} object.
     */
    public abstract String getUrlName();

    /**
     * All registered instances.
     */
    public static final List<ManagementLink> LIST = new CopyOnWriteArrayList<ManagementLink>();
}
