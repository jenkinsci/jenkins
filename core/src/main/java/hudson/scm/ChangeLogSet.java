package hudson.scm;

import hudson.MarkupText;
import hudson.Util;
import hudson.api.Exposed;
import hudson.api.ExposedBean;
import hudson.model.AbstractBuild;
import hudson.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents SCM change list.
 *
 * Use the "index" view of this object to render the changeset detail page,
 * and use the "digest" view of this object to render the summary page.
 *
 * @author Kohsuke Kawaguchi
 */
@ExposedBean(defaultVisibility=999)
public abstract class ChangeLogSet<T extends ChangeLogSet.Entry> implements Iterable<T> {

    /**
     * {@link AbstractBuild} whose change log this object represents.
     */
    public final AbstractBuild<?,?> build;

    protected ChangeLogSet(AbstractBuild<?, ?> build) {
        this.build = build;
    }

    /**
     * Returns true if there's no change.
     */
    public abstract boolean isEmptySet();

    /**
     * All changes in the change set.
     */
    // method for the remote API.
    @Exposed
    public final Object[] getItems() {
        List<T> r = new ArrayList<T>();
        for (T t : this)
            r.add(t);
        return r.toArray();
    }

    /**
     * Constant instance that represents no changes.
     */
    public static ChangeLogSet<? extends ChangeLogSet.Entry> createEmpty(AbstractBuild build) {
        return new CVSChangeLogSet(build,Collections.<CVSChangeLogSet.CVSChangeLog>emptyList());
    }

    @ExposedBean(defaultVisibility=999)
    public static abstract class Entry {
        private ChangeLogSet parent;

        public ChangeLogSet getParent() {
            return parent;
        }

        /**
         * Should be invoked before a {@link ChangeLogSet} is exposed to public.
         */
        protected void setParent(ChangeLogSet parent) {
            this.parent = parent;
        }

        /**
         * Gets the "commit message".
         *
         * <p>
         * The exact definition depends on the individual SCM implementation.
         *
         * @return
         *      Can be empty but never null.
         */
        public abstract String getMsg();

        /**
         * The user who made this change.
         *
         * @return
         *      never null.
         */
        public abstract User getAuthor();

        /**
         * Returns a set of paths in the workspace that was
         * affected by this change.
         *
         * <p>
         * Contains string like 'foo/bar/zot'. No leading/trailing '/',
         * and separator must be normalized to '/'.
         *
         * @return never null.
         */
        public abstract Collection<String> getAffectedPaths();

        /**
         * Gets the text fully marked up by {@link ChangeLogAnnotator}.
         */
        public String getMsgAnnotated() {
            MarkupText markup = new MarkupText(getMsgEscaped());
            for (ChangeLogAnnotator a : ChangeLogAnnotator.annotators)
                a.annotate(parent.build,this,markup);

            return markup.toString();
        }

        /**
         * Message escaped for HTML
         */
        public String getMsgEscaped() {
            return Util.escape(getMsg());
        }
    }
}
