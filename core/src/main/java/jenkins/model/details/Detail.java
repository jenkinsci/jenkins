package jenkins.model.details;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Actionable;
import hudson.model.ModelObject;
import hudson.model.Run;
import org.jenkins.ui.icon.IconSpec;

/**
 * {@link Detail} represents a piece of information about a {@link Run}.
 * Such information could include:
 * <ul>
 *  <li>the date and time the run started</li>
 *  <li>the amount of time the run took to complete</li>
 *  <li>SCM information for the build</li>
 *  <li>who kicked the build off</li>
 * </ul>
 * @since 2.498
 */
public abstract class Detail implements ModelObject, IconSpec {

    private final Actionable object;

    public Detail(Actionable object) {
        this.object = object;
    }

    public Actionable getObject() {
        return object;
    }

    /**
     * {@inheritDoc}
     */
    public @Nullable String getIconClassName() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable String getDisplayName() {
        return null;
    }

    /**
     * Optional URL for the {@link Detail}.
     * If provided the detail element will be a link instead of plain text.
     */
    public @Nullable String getLink() {
        return null;
    }

    /**
     * @return the grouping of the detail
     */
    public DetailGroup getGroup() {
        return GeneralDetailGroup.get();
    }

    /**
     * @return order in the group, MAX_VALUE is first, zero is any order
     */
    public int getOrder() {
        return 0;
    }

    /**
     * @return where this detail should be shown, defaults to {@link Visibility#FULL}
     * @since TODO
     */
    public @NonNull Visibility getVisibility() {
        return Visibility.FULL;
    }

    /**
     * Where a {@link Detail} should be shown.
     * @since TODO
     */
    public enum Visibility {
        /**
         * Only shown in condensed views, such as rows in the build history.
         */
        COMPACT,
        /**
         * Only shown in full views, such as the overview page of a run.
         */
        FULL,
        /**
         * Shown in both compact and full views.
         */
        BOTH;

        /**
         * @param context the view the detail is being rendered in, {@code null} or {@link #BOTH} to match any
         * @return whether a detail with this visibility should be shown in the given context
         */
        public boolean isVisibleIn(@CheckForNull Visibility context) {
            return this == BOTH || context == null || context == BOTH || this == context;
        }
    }
}
