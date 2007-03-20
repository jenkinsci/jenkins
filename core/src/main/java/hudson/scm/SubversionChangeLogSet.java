package hudson.scm;

import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.scm.SubversionChangeLogSet.LogEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.AbstractList;

/**
 * {@link ChangeLogSet} for Subversion.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SubversionChangeLogSet extends ChangeLogSet<LogEntry> {
    private final List<LogEntry> logs;

    /**
     * @GuardedBy this
     */
    private Map<String,Long> revisionMap;

    /*package*/ SubversionChangeLogSet(AbstractBuild build, List<LogEntry> logs) {
        super(build);
        this.logs = Collections.unmodifiableList(logs);
        for (LogEntry log : logs)
            log.setParent(this);
    }

    public boolean isEmptySet() {
        return logs.isEmpty();
    }

    public List<LogEntry> getLogs() {
        return logs;
    }


    public Iterator<LogEntry> iterator() {
        return logs.iterator();
    }

    public synchronized Map<String,Long> getRevisionMap() throws IOException {
        if(revisionMap==null)
            revisionMap = SubversionSCM.parseRevisionFile(build);
        return revisionMap;
    }

    /**
     * One commit.
     */
    public static class LogEntry extends ChangeLogSet.Entry {
        private int revision;
        private User author;
        private String date;
        private String msg;
        private List<Path> paths = new ArrayList<Path>();

        public int getRevision() {
            return revision;
        }

        public void setRevision(int revision) {
            this.revision = revision;
        }

        public User getAuthor() {
            return author;
        }

        public Collection<String> getAffectedPaths() {
            return new AbstractList<String>() {
                public String get(int index) {
                    return paths.get(index).value;
                }
                public int size() {
                    return paths.size();
                }
            };
        }

        public void setUser(String author) {
            this.author = User.get(author);
        }

        public String getUser() {// digester wants read/write property, even though it never reads. Duh.
            return author.getDisplayName();
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public void addPath( Path p ) {
            p.entry = this;
            paths.add(p);
        }

        public List<Path> getPaths() {
            return paths;
        }
    }

    /**
     * A file in a commit.
     */
    public static class Path {
        private LogEntry entry;
        private char action;
        private String value;

        public LogEntry getLogEntry() {
            return entry;
        }

        public void setAction(String action) {
            this.action = action.charAt(0);
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public EditType getEditType() {
            if( action=='A' )
                return EditType.ADD;
            if( action=='D' )
                return EditType.DELETE;
            return EditType.EDIT;
        }
    }
}
