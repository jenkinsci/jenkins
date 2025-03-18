package jenkins.views;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Action;
import java.util.List;
import java.util.Optional;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Extension point that provides capabilities to render a specific header.
 *
 * Extend {@link PartialHeader} or {@link FullHeader} depending on the use case.
 *
 * The default Jenkins header is provided as an implementation of a {@link FullHeader}
 * named {@link JenkinsHeader}.
 *
 * The first header located will be used, set the ordinal field on Extension to have a higher priority.
 *
 * The header content will be injected inside the {@code pageHeader.jelly}, based on the header
 * retrieved by the {@link Header#get()} method. That header content will be provided
 * inside a resource called {@code headerContent.jelly}. It performs a full replacement
 * of the header.
 *
 * @see PartialHeader
 * @see FullHeader
 * @see JenkinsHeader
 * @since 2.323
 */
public abstract class Header implements ExtensionPoint {

    /**
     * Checks if header is available
     * @return if header is available
     */
    public boolean isAvailable() {
        return isCompatible() && isEnabled();
    }

    /**
     * Checks API compatibility of the header
     * @return if header is compatible
     */
    public abstract boolean isCompatible();

    /**
     * Checks if header is enabled.
     * @return if header is enabled
     */
    public abstract boolean isEnabled();

    @Restricted(NoExternalUse.class)
    public static Header get() {
        Optional<Header> header = ExtensionList.lookup(Header.class).stream().filter(Header::isAvailable).findFirst();
        return header.orElseGet(JenkinsHeader::new);
    }

    /**
     * @return a list of {@link Action} to show in the header, defaults to {@link hudson.model.RootAction} extensions
     */
    @Restricted(NoExternalUse.class)
    public List<Action> getActions() {
        return Jenkins.get()
                .getActions()
                .stream()
                .filter(e -> e.getIconFileName() != null)
                .toList();
    }
}
