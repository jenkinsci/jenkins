package hudson.model;

import hudson.Util;
import hudson.scm.ChangeLogSet.Entry;
import hudson.util.RunList;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the rendering of the list of {@link TopLevelItem}s
 * that {@link Hudson} owns.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class View extends AbstractModelObject {

    /**
     * Gets all the items in this collection in a read-only view.
     */
    public abstract Collection<TopLevelItem> getItems();

    /**
     * Checks if the job is in this collection.
     */
    public abstract boolean contains(TopLevelItem item);

    /**
     * Gets the name of all this collection.
     */
    public abstract String getViewName();

    /**
     * Message displayed in the top page. Can be null. Includes HTML.
     */
    public abstract String getDescription();

    /**
     * Returns the path relative to the context root.
     */
    public abstract String getUrl();

    public static final class UserInfo implements Comparable<UserInfo> {
        private final User user;
        private Calendar lastChange;
        private AbstractProject project;

        UserInfo(User user, AbstractProject p, Calendar lastChange) {
            this.user = user;
            this.project = p;
            this.lastChange = lastChange;
        }

        public User getUser() {
            return user;
        }

        public Calendar getLastChange() {
            return lastChange;
        }

        public AbstractProject getProject() {
            return project;
        }

        /**
         * Returns a human-readable string representation of when this user was last active.
         */
        public String getLastChangeTimeString() {
            long duration = new GregorianCalendar().getTimeInMillis()-lastChange.getTimeInMillis();
            return Util.getTimeSpanString(duration);
        }

        public String getTimeSortKey() {
            return Util.XS_DATETIME_FORMATTER.format(lastChange.getTime());
        }

        public int compareTo(UserInfo that) {
            long rhs = that.lastChange.getTimeInMillis();
            long lhs = this.lastChange.getTimeInMillis();
            if(rhs>lhs) return 1;
            if(rhs<lhs) return -1;
            return 0;
        }
    }

    /**
     * Does this {@link View} has any associated user information recorded?
     */
    public final boolean hasPeople() {
        for (Item item : getItems()) {
            for (Job job : item.getAllJobs()) {
                if (job instanceof AbstractProject) {
                    AbstractProject<?,?> p = (AbstractProject) job;
                    for (AbstractBuild<?,?> build : p.getBuilds()) {
                        for (Entry entry : build.getChangeSet()) {
                            User user = entry.getAuthor();
                            if(user!=null)
                                return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Gets the users that show up in the changelog of this job collection.
     */
    public final List<UserInfo> getPeople() {
        Map<User,UserInfo> users = new HashMap<User,UserInfo>();
        for (Item item : getItems()) {
            for (Job job : item.getAllJobs()) {
                if (job instanceof AbstractProject) {
                    AbstractProject<?,?> p = (AbstractProject) job;
                    for (AbstractBuild<?,?> build : p.getBuilds()) {
                        for (Entry entry : build.getChangeSet()) {
                            User user = entry.getAuthor();

                            UserInfo info = users.get(user);
                            if(info==null)
                                users.put(user,new UserInfo(user,p,build.getTimestamp()));
                            else
                            if(info.getLastChange().before(build.getTimestamp())) {
                                info.project = p;
                                info.lastChange = build.getTimestamp();
                            }
                        }
                    }
                }
            }
        }

        List<UserInfo> r = new ArrayList<UserInfo>(users.values());
        Collections.sort(r);

        return r;
    }

    /**
     * Creates a new {@link Item} in this collection.
     *
     * @return
     *      null if fails.
     */
    public abstract Item doCreateItem( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException;

    public void doRssAll( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " all builds", new RunList(this));
    }

    public void doRssFailed( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rss(req, rsp, " failed builds", new RunList(this).failureOnly());
    }

    private void rss(StaplerRequest req, StaplerResponse rsp, String suffix, RunList runs) throws IOException, ServletException {
        RSS.forwardToRss(getDisplayName()+ suffix, getUrl(),
            runs.newBuilds(), Run.FEED_ADAPTER, req, rsp );
    }

    public static final Comparator<View> SORTER = new Comparator<View>() {
        public int compare(View lhs, View rhs) {
            return lhs.getViewName().compareTo(rhs.getViewName());
        }
    };
}
