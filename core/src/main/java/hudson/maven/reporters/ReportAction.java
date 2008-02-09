package hudson.maven.reporters;

import hudson.model.Action;
import org.apache.maven.reporting.MavenReport;

import java.util.List;
import java.util.ArrayList;

/**
 * {@link Action} to display links to the generated {@link MavenReport Maven reports}.
 * @author Kohsuke Kawaguchi
 */
public final class ReportAction implements Action {

    private final List<Entry> entries = new ArrayList<Entry>();

    public static final class Entry {
        /**
         * Relative path to the top of the report withtin the project reporting directory.
         */
        public final String path;
        public final String title;

        public Entry(String path, String title) {
            this.path = path;
            this.title = title;
        }
    }

    public ReportAction() {
    }

    protected void add(Entry e) {
        entries.add(e);
    }

    public String getIconFileName() {
        // TODO
        return "n/a.gif";
    }

    public String getDisplayName() {
        return Messages.ReportAction_DisplayName();
    }

    public String getUrlName() {
        return "mavenReports";
    }
}
