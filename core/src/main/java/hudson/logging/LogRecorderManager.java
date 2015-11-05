/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.logging;

import hudson.FeedAdapter;
import hudson.Functions;
import hudson.init.Initializer;
import static hudson.init.InitMilestone.PLUGINS_PREPARED;
import hudson.model.AbstractModelObject;
import jenkins.model.Jenkins;
import hudson.model.RSS;
import hudson.util.CopyOnWriteMap;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpRedirect;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Owner of {@link LogRecorder}s, bound to "/log".
 *
 * @author Kohsuke Kawaguchi
 */
public class LogRecorderManager extends AbstractModelObject implements ModelObjectWithChildren {
    /**
     * {@link LogRecorder}s keyed by their {@linkplain LogRecorder#name name}.
     */
    public transient final Map<String,LogRecorder> logRecorders = new CopyOnWriteMap.Tree<String,LogRecorder>();

    public String getDisplayName() {
        return Messages.LogRecorderManager_DisplayName();
    }

    public String getSearchUrl() {
        return "/log";
    }

    public LogRecorder getDynamic(String token) {
        return getLogRecorder(token);
    }

    public LogRecorder getLogRecorder(String token) {
        return logRecorders.get(token);
    }

    static File configDir() {
        return new File(Jenkins.getInstance().getRootDir(), "log");
    }

    /**
     * Loads the configuration from disk.
     */
    public void load() throws IOException {
        logRecorders.clear();
        File dir = configDir();
        File[] files = dir.listFiles((FileFilter)new WildcardFileFilter("*.xml"));
        if(files==null)     return;
        for (File child : files) {
            String name = child.getName();
            name = name.substring(0,name.length()-4);   // cut off ".xml"
            LogRecorder lr = new LogRecorder(name);
            lr.load();
            logRecorders.put(name,lr);
        }
    }

    /**
     * Creates a new log recorder.
     */
    public HttpResponse doNewLogRecorder(@QueryParameter String name) {
        Jenkins.checkGoodName(name);
        
        logRecorders.put(name,new LogRecorder(name));

        // redirect to the config screen
        return new HttpRedirect(name+"/configure");
    }

    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        ContextMenu menu = new ContextMenu();
        menu.add("all","All Jenkins Logs");
        for (LogRecorder lr : logRecorders.values()) {
            menu.add(lr.getSearchUrl(), lr.getDisplayName());
        }
        return menu;
    }

    /**
     * Configure the logging level.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("LG_LOST_LOGGER_DUE_TO_WEAK_REFERENCE")
    public HttpResponse doConfigLogger(@QueryParameter String name, @QueryParameter String level) {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        Level lv;
        if(level.equals("inherit"))
            lv = null;
        else
            lv = Level.parse(level.toUpperCase(Locale.ENGLISH));
        Logger.getLogger(name).setLevel(lv);
        return new HttpRedirect("levels");
    }

    /**
     * RSS feed for log entries.
     */
    public void doRss( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        doRss(req, rsp, Jenkins.logRecords);
    }

    /**
     * Renders the given log recorders as RSS.
     */
    /*package*/ static void doRss(StaplerRequest req, StaplerResponse rsp, List<LogRecord> logs) throws IOException, ServletException {
        // filter log records based on the log level
        String level = req.getParameter("level");
        if(level!=null) {
            Level threshold = Level.parse(level);
            List<LogRecord> filtered = new ArrayList<LogRecord>();
            for (LogRecord r : logs) {
                if(r.getLevel().intValue() >= threshold.intValue())
                    filtered.add(r);
            }
            logs = filtered;
        }

        RSS.forwardToRss("Hudson log","", logs, new FeedAdapter<LogRecord>() {
            public String getEntryTitle(LogRecord entry) {
                return entry.getMessage();
            }

            public String getEntryUrl(LogRecord entry) {
                return "log";   // TODO: one URL for one log entry?
            }

            public String getEntryID(LogRecord entry) {
                return String.valueOf(entry.getSequenceNumber());
            }

            public String getEntryDescription(LogRecord entry) {
                return Functions.printLogRecord(entry);
            }

            public Calendar getEntryTimestamp(LogRecord entry) {
                GregorianCalendar cal = new GregorianCalendar();
                cal.setTimeInMillis(entry.getMillis());
                return cal;
            }

            public String getEntryAuthor(LogRecord entry) {
                return JenkinsLocationConfiguration.get().getAdminAddress();
            }
        },req,rsp);
    }

    @Initializer(before=PLUGINS_PREPARED)
    public static void init(Jenkins h) throws IOException {
        h.getLog().load();
    }
}
