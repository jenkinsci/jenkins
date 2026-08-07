/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.scm;

import hudson.MarkupText;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Represents SCM change list.
 *
 * <p>
 * Use the "index" view of this object to render the changeset detail page,
 * and use the "digest" view of this object to render the summary page.
 * For the change list at project level, see {@link SCM}.
 *
 * <p>
 * {@link Iterator} is expected to return newer changes first then older changes later.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean(defaultVisibility = 999)
public abstract class ChangeLogSet<T extends ChangeLogSet.Entry> implements Iterable<T> {

    /**
     * Build whose change log this object represents.
     */
    private final Run<?, ?> run;
    @Deprecated
    public final AbstractBuild<?, ?> build;
    private final RepositoryBrowser</* ideally T */?> browser;

    /**
     * @since 1.568
     */
    protected ChangeLogSet(Run<?, ?> run, RepositoryBrowser<?> browser) {
        this.run = run;
        build = run instanceof AbstractBuild ? (AbstractBuild) run : null;
        this.browser = browser;
    }

    @Deprecated
    protected ChangeLogSet(AbstractBuild<?, ?> build) {
        this(build, browserFromBuild(build));
    }

    private static RepositoryBrowser<?> browserFromBuild(AbstractBuild<?, ?> build) {
        if (build == null) { // not generally allowed, but sometimes done in unit tests
            return null;
        }
        return build.getParent().getScm().getEffectiveBrowser();
    }

    /**
     * @since 1.568
     */
    public Run<?, ?> getRun() {
        return run;
    }

    /**
     * @since 1.568
     */
    public RepositoryBrowser<?> getBrowser() {
        return browser;
    }

    /**
     * Returns true if there's no change.
     */
    public abstract boolean isEmptySet();

    /**
     * All changes in this change set.
     */
    // method for the remote API.
    @Exported
    public final Object[] getItems() {
        List<T> r = new ArrayList<>();
        for (T t : this)
            r.add(t);
        return r.toArray();
    }

    /**
     * Optional identification of the kind of SCM being used.
     * @return a short token, such as the SCM's main CLI executable name
     * @since 1.284
     */
    @Exported
    public String getKind() {
        return null;
    }

    /**
     * Constant instance that represents no changes.
     * @since 1.568
     */
    public static ChangeLogSet<? extends ChangeLogSet.Entry> createEmpty(Run build) {
        return new EmptyChangeLogSet(build);
    }

    @Deprecated
    public static ChangeLogSet<? extends ChangeLogSet.Entry> createEmpty(AbstractBuild build) {
        return createEmpty((Run) build);
    }

    @ExportedBean(defaultVisibility = 999)
    public abstract static class Entry {
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
         * Returns a human readable display name of the commit number, revision number, and such thing
         * that identifies this entry.
         *
         * <p>
         * This method is primarily intended for visualization of the data.
         *
         * @return
         *      null if such a concept doesn't make sense for the implementation. For example,
         *      in CVS there's no single identifier for commits. Each file gets a different revision number.
         * @since 1.405
         */
        @Exported
        public String getCommitId() {
            return null;
        }

        /**
         * Returns the timestamp of this commit in the {@link Date#getTime()} format.
         *
         * <p>
         * This method is primarily intended for visualization of the data.
         *
         * @return
         *      -1 if the implementation doesn't support it (for example, in CVS a commit
         *      spreads over time between multiple changes on multiple files, so there's no single timestamp.)
         * @since 1.405
         */
        @Exported
        public long getTimestamp() {
            return -1;
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
        @Exported
        public abstract String getMsg();

        /**
         * The user who made this change.
         *
         * @return
         *      never null.
         */
        @Exported
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
        @Exported
        public abstract Collection<String> getAffectedPaths();

        /**
         * Returns a set of paths in the workspace that was
         * affected by this change.
         * <p>
         * Noted: since this is a new interface, some of the SCMs may not have
         * implemented this interface. The default implementation for this
         * interface is throw UnsupportedOperationException
         * <p>
         * It doesn't throw NoSuchMethodException because I rather to throw a
         * runtime exception
         *
         * @return AffectedFile never null.
         * @since 1.309
         */
        public Collection<? extends AffectedFile> getAffectedFiles() {
            String scm = "this SCM";
            ChangeLogSet parent = getParent();
            if (null != parent) {
                String kind = parent.getKind();
                if (null != kind && !kind.trim().isEmpty()) scm = kind;
            }
            throw new UnsupportedOperationException("getAffectedFiles() is not implemented by " + scm);
        }

        /**
         * Gets the text fully marked up by {@link ChangeLogAnnotator}.
         */
        public String getMsgAnnotated() {
            MarkupText markup = new MarkupText(getMsg());
            for (ChangeLogAnnotator a : ChangeLogAnnotator.all())
                try {
                    a.annotate(parent.run, this, markup);
                } catch (RuntimeException e) {
                    LOGGER.info("ChangeLogAnnotator " + a.toString() + " failed to annotate message '" + getMsg() + "'; " + e.getMessage());
                } catch (Error e) {
                    LOGGER.severe("ChangeLogAnnotator " + a + " failed to annotate message '" + getMsg() + "'; " + e.getMessage());
                }

            return markup.toString(false);
        }

        /**
         * Message escaped for HTML
         */
        public String getMsgEscaped() {
            return Util.escape(getMsg());
        }

        static final Logger LOGGER = Logger.getLogger(ChangeLogSet.Entry.class.getName());
    }

    /**
     * Represents a file change. Contains filename, edit type, etc.
     *
     * I checked the API names against some some major SCMs and most SCMs
     * can adapt to this interface with very little changes
     *
     * @see ChangeLogSet.Entry#getAffectedFiles()
     */
    public interface AffectedFile {
        /**
         * The path in the workspace that was affected
         * <p>
         * Contains string like 'foo/bar/zot'. No leading/trailing '/',
         * and separator must be normalized to '/'.
         *
         * @return never null.
         */
        String getPath();


        /**
         * Return whether the file is new/modified/deleted
         */
        EditType getEditType();
    }
}
