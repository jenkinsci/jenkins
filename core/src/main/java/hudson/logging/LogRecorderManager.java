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
package hudson.logging;

import hudson.FeedAdapter;
import hudson.Functions;
import hudson.model.AbstractModelObject;
import hudson.model.Hudson;
import hudson.model.RSS;
import hudson.tasks.Mailer;
import hudson.util.CopyOnWriteMap;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Owner of {@link LogRecorder}s, bound to "/log".
 *
 * @author Kohsuke Kawaguchi
 */
public class LogRecorderManager extends AbstractModelObject {
    /**
     * {@link LogRecorder}s.
     */
    public transient final Map<String,LogRecorder> logRecorders = new CopyOnWriteMap.Tree<String,LogRecorder>();

    public String getDisplayName() {
        return "log";
    }

    public String getSearchUrl() {
        return "/log";
    }

    public LogRecorder getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        return getLogRecorder(token);
    }

    public LogRecorder getLogRecorder(String token) {
        return logRecorders.get(token);
    }

    /**
     * Loads the configuration from disk.
     */
    public void load() throws IOException {
        logRecorders.clear();
        File dir = new File(Hudson.getInstance().getRootDir(), "log");
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
    public void doNewLogRecorder( StaplerRequest req, StaplerResponse rsp, @QueryParameter String name) throws IOException, ServletException {
        try {
            Hudson.checkGoodName(name);
        } catch (ParseException e) {
            sendError(e, req, rsp);
            return;
        }
        
        logRecorders.put(name,new LogRecorder(name));

        // redirect to the config screen
        rsp.sendRedirect2(name+"/configure");
    }

    /**
     * Configure the logging level.
     */
    public void doConfigLogger(StaplerResponse rsp, @QueryParameter String name, @QueryParameter String level) throws IOException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        Level lv;
        if(level.equals("inherit"))
            lv = null;
        else
            lv = Level.parse(level.toUpperCase());
        Logger.getLogger(name).setLevel(lv);
        rsp.sendRedirect2("all");
    }

    /**
     * RSS feed for log entries.
     */
    public void doRss( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        doRss(req, rsp, Hudson.logRecords);
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
                return Mailer.descriptor().getAdminAddress();
            }
        },req,rsp);
    }
}
