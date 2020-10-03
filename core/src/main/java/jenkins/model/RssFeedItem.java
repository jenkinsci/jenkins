package jenkins.model;

import hudson.model.Run;
import hudson.scm.ChangeLogSet;

/**
 * The type Rss feed item for Rss changelog.
 */
public class RssFeedItem {
    private final ChangeLogSet.Entry entry;
    private final int index;

    /**
     * Instantiates a new Rss feed item.
     *
     * @param e   the e
     * @param idx the idx
     */
    public RssFeedItem(ChangeLogSet.Entry e, int idx) {
        this.entry = e;
        this.index = idx;
    }

    /**
     * Gets the run.
     *
     * @return the run
     */
    public Run<?, ?> getRun() {
        return entry.getParent().getRun();
    }

    /**
     * Gets the entry.
     *
     * @return the entry
     */
    public ChangeLogSet.Entry getEntry() {
        return entry;
    }

    /**
     * Gets the index.
     *
     * @return the index
     */
    public int getIndex() {
        return index;
    }
}
