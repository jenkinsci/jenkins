package hudson.scm;

import hudson.ExtensionPoint;
import hudson.MarkupText;
import hudson.util.CopyOnWriteList;
import hudson.scm.ChangeLogSet.Entry;
import hudson.model.AbstractBuild;

import java.util.logging.Logger;

/**
 * Performs mark up on changelog messages to be displayed.
 *
 * <p>
 * SCM changelog messages are usually plain text, but when we display that in Hudson,
 * it is often nice to be able to put mark up on the text (for example to link to
 * external issue tracking system.)
 *
 * <p>
 * Plugins that are interested in doing so may extend this class and call {@link #register()}.
 * When multiple annotators are registered, their results will be combined.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.70
 */
public abstract class ChangeLogAnnotator implements ExtensionPoint {
    /**
     * Called by Hudson to allow markups to be added to the changelog text.
     *
     * <p>
     * This method is invoked each time a page is rendered, so implementations
     * of this method should not take too long to execute. Also note that
     * this method may be invoked concurrently by multiple threads.
     *
     * <p>
     * If there's any error during the processing, it should be recorded in
     * {@link Logger} and the method should return normally.
     *
     * @param build
     *      Build that owns this changelog. From here you can access broader contextual
     *      information, like the project, or it settings. Never null.
     * @param change
     *      The changelog entry for which this method is adding markup.
     *      Never null.
     * @param text
     *      The text and markups. Implementation of this method is expected to
     *      add additional annotations into this object. If other annotators
     *      are registered, the object may already contain some markups when this
     *      method is invoked. Never null. {@link MarkupText#getText()} on this instance
     *      will return the same string as {@link Entry#getMsgEscaped()}.
     */
    public abstract void annotate(AbstractBuild<?,?> build, Entry change, MarkupText text );

    /**
     * Registers this annotator, so that Hudson starts using this object
     * for adding markup.
     */
    public final void register() {
        annotators.add(this);
    }

    /**
     * Unregisters this annotator, so that Hudson stops using this object.
     */
    public final boolean unregister() {
        return annotators.remove(this);
    }

    /**
     * All registered {@link ChangeLogAnnotator}s.
     */
    public static final CopyOnWriteList<ChangeLogAnnotator> annotators = new CopyOnWriteList<ChangeLogAnnotator>();
}
