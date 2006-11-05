package hudson;

import java.util.Calendar;

/**
 * Provides a RSS feed view of the data.
 *
 * @author Kohsuke Kawaguchi
 */
public interface FeedAdapter<E> {
    String getEntryTitle(E entry);
    String getEntryUrl(E entry);
    String getEntryID(E entry);
    Calendar getEntryTimestamp(E entry);
}
