package jenkins.views;

import hudson.model.Action;
import java.util.List;

import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link Header} that provides its own resources as full replacement. It does not
 * depends on any core resource (images, CSS, JS, etc.)
 *
 * Given this kind of header is totally independent, it will be compatible by default.
 *
 * @see Header
 */
public abstract class FullHeader extends Header {

    public boolean isCompatible() {
        return true;
    }

    /**
     * @return a list of {@link Action} to show in the header, defaults to {@link hudson.model.RootAction} extensions
     */
    @Restricted(NoExternalUse.class)
    public List<HeaderAction> getActions() {
        return Jenkins.get()
                .getActions()
                .stream()
                .filter(e -> e.getIconFileName() != null)
                .map(e -> (RootAction) e)
                .map(e -> new HeaderAction(e.getDisplayName(), e.getIconFileName(), e.getUrlName(), e.getBadge()))
                .toList();
    }
}
