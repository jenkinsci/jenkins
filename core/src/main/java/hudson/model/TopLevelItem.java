package hudson.model;

import hudson.ExtensionPoint;

/**
 * {@link Item} that can be directly displayed under {@link Hudson}. 
 *
 * <p>
 * To register a custom {@link TopLevelItem} class from a plugin, add it to
 * {@link Items#LIST}. Also see {@link Items#XSTREAM}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface TopLevelItem extends Item, ExtensionPoint, Describable<TopLevelItem> {
    /**
     * By definition the parent of the top-level item is always {@link Hudson}.
     */
    Hudson getParent();

    /**
     *
     * @see Describable#getDescriptor()
     */
    TopLevelItemDescriptor getDescriptor();
}
