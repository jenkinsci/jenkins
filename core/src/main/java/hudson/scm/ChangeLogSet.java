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
import hudson.model.User;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents SCM change list.
 *
 * <p>
 * Use the "index" view of this object to render the changeset detail page,
 * and use the "digest" view of this object to render the summary page.
 * For the change list at project level, see {@link SCM}.
 *
 * <p>
 * {@link Iterator} is expected to return recent changes first.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean(defaultVisibility=999)
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
     * All changes in this change set. 
     */
    // method for the remote API.
    @Exported
    public final Object[] getItems() {
        List<T> r = new ArrayList<T>();
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
     */
    public static ChangeLogSet<? extends ChangeLogSet.Entry> createEmpty(AbstractBuild build) {
        return new CVSChangeLogSet(build,Collections.<CVSChangeLogSet.CVSChangeLog>emptyList());
    }

    @ExportedBean(defaultVisibility=999)
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
	        if ( null != parent ) {
		        String kind = parent.getKind();
		        if ( null != kind && kind.trim().length() > 0 ) scm = kind;
	        }
	        throw new UnsupportedOperationException("getAffectedFiles() is not implemented by " + scm);
        }

        /**
         * Gets the text fully marked up by {@link ChangeLogAnnotator}.
         */
        public String getMsgAnnotated() {
            MarkupText markup = new MarkupText(getMsgEscaped());
            for (ChangeLogAnnotator a : ChangeLogAnnotator.all())
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
	    public String getPath();
	    
	    
	    /**
	     * Return whether the file is new/modified/deleted
	     *
	     * @return EditType
	     * @see EditType
	     */
	    public EditType getEditType();
    }
}
