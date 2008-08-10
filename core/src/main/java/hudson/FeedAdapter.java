package hudson;

import java.util.Calendar;

/**
 * Provides a RSS feed view of the data.
 *
 * @author Kohsuke Kawaguchi
 */
public interface FeedAdapter<E> {
    /**
     * Gets the human readable title of the entry.
     * In RSS readers, this is usually displayed like an e-mail subject.
     */
    String getEntryTitle(E entry);

    /**
     * Gets the URL that represents this entry.
     * Relative to context root of the Hudson.
     */
    String getEntryUrl(E entry);

    /**
     * Unique ID of each entry.
     * RSS readers use this to determine what feeds are new and what are not.
     *
     * This needs to produce a tag URL as per RFC 4151, required by Atom 1.0.
     */
    String getEntryID(E entry);

    /**
     * (Potentially lengthy) plain text to be attached to the feed.
     * Can be null.
     */
    String getEntryDescription(E entry);

    /**
     * Timestamp of the last change in this entry.
     */
    Calendar getEntryTimestamp(E entry);

    /**
     * Author of this entry.
     */
    String getEntryAuthor(E entry);
}
