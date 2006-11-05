package hudson.scm;

import hudson.model.User;

import java.util.Collections;

/**
 * Represents SCM change list.
 *
 * Use the "index" view of this object to render the changeset detail page,
 * and use the "digest" view of this object to render the summary page.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ChangeLogSet<T extends ChangeLogSet.Entry> implements Iterable<T> {
    /**
     * Returns true if there's no change.
     */
    public abstract boolean isEmptySet();

    /**
     * Constant instance that represents no changes.
     */
    public static final ChangeLogSet<? extends Entry> EMPTY = new CVSChangeLogSet(Collections.EMPTY_LIST);

    public static abstract class Entry {

        public abstract String getMsg();

        /**
         * The user who made this change.
         *
         * @return
         *      never null.
         */
        public abstract User getAuthor();

        /**
         * Message escaped for HTML
         */
        public String getMsgEscaped() {
            String msg = getMsg();
            StringBuffer buf = new StringBuffer(msg.length()+64);
            for( int i=0; i<msg.length(); i++ ) {
                char ch = msg.charAt(i);
                if(ch=='\n')
                    buf.append("<br>");
                else
                if(ch=='<')
                    buf.append("&lt;");
                else
                if(ch=='&')
                    buf.append("&amp;");
                else
                if(ch==' ')
                    buf.append("&nbsp;");
                else
                    buf.append(ch);
            }
            return buf.toString();
        }
    }
}
