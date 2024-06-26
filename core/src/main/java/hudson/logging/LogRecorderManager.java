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

import static hudson.init.InitMilestone.PLUGINS_PREPARED;
import static java.util.stream.Collectors.toMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FeedAdapter;
import hudson.Functions;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.init.Initializer;
import hudson.model.AbstractModelObject;
import hudson.model.Failure;
import hudson.model.RSS;
import hudson.util.CopyOnWriteMap;
import hudson.util.FormValidation;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import jenkins.util.SystemProperties;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Owner of {@link LogRecorder}s, bound to "/log".
 *
 * @author Kohsuke Kawaguchi
 */
public class LogRecorderManager extends AbstractModelObject implements ModelObjectWithChildren, StaplerProxy {

    private static final Logger LOGGER = Logger.getLogger(LogRecorderManager.class.getName());

    /**
     * {@link LogRecorder}s keyed by their {@linkplain LogRecorder#getName()}  name}.
     *
     * @deprecated use {@link #getRecorders()} instead
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.323")
    public final transient Map<String, LogRecorder> logRecorders = new CopyOnWriteMap.Tree<>();

    private List<LogRecorder> recorders;

    @DataBoundConstructor
    public LogRecorderManager() {
        this.recorders = new ArrayList<>();
    }

    public List<LogRecorder> getRecorders() {
        return recorders;
    }

    @DataBoundSetter
    public void setRecorders(List<LogRecorder> recorders) {
        this.recorders = recorders;

        Map<String, LogRecorder> values = recorders.stream()
                .collect(toMap(
                        LogRecorder::getName,
                        Function.identity(),
                        // see JENKINS-68752, ignore duplicates
                        (recorder1, recorder2) -> {
                            LOGGER.warning(String.format("Ignoring duplicate log recorder '%s', check $JENKINS_HOME/log and remove the duplicate recorder", recorder2.getName()));
                            return recorder1;
                        }));
        ((CopyOnWriteMap<String, LogRecorder>) logRecorders).replaceBy(values);
    }

    @Override
    public String getDisplayName() {
        return Messages.LogRecorderManager_DisplayName();
    }

    @Override
    public String getSearchUrl() {
        return "/log";
    }

    public LogRecorder getDynamic(String token) {
        return getLogRecorder(token);
    }

    public LogRecorder getLogRecorder(String token) {
        return recorders.stream().filter(logRecorder -> logRecorder.getName().equals(token)).findAny().orElse(null);
    }

    static File configDir() {
        return new File(Jenkins.get().getRootDir(), "log");
    }

    /**
     * Loads the configuration from disk.
     */
    public void load() throws IOException {
        recorders.clear();
        File dir = configDir();
        File[] files = dir.listFiles((FileFilter) new WildcardFileFilter("*.xml"));
        if (files == null)     return;
        for (File child : files) {
            String name = child.getName();
            name = name.substring(0, name.length() - 4);   // cut off ".xml"
            LogRecorder lr = new LogRecorder(name);
            lr.load();
            recorders.add(lr);
        }
        setRecorders(recorders); // ensure that legacy logRecorders field is synced on load
    }

    /**
     * Creates a new log recorder.
     */
    @RequirePOST
    public HttpResponse doNewLogRecorder(@QueryParameter String name) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        Jenkins.checkGoodName(name);

        recorders.add(new LogRecorder(name));

        // redirect to the config screen
        return new HttpRedirect(name + "/configure");
    }

    @Restricted(NoExternalUse.class)
    public FormValidation doCheckNewName(@QueryParameter String name) {
        if (Util.fixEmpty(name) == null) {
            return FormValidation.ok();
        }

        try {
            Jenkins.checkGoodName(name);
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }
        return FormValidation.ok();
    }

    @Override
    public ContextMenu doChildrenContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        ContextMenu menu = new ContextMenu();
        menu.add("all", "All Jenkins Logs");
        for (LogRecorder lr : recorders) {
            menu.add(lr.getSearchUrl(), lr.getDisplayName());
        }
        return menu;
    }

    /**
     * Configure the logging level.
     */
    @RequirePOST
    @SuppressFBWarnings(
            value = "LG_LOST_LOGGER_DUE_TO_WEAK_REFERENCE",
            justification =
                    "if the logger is known, then we have a reference to it in LogRecorder#loggers")
    public HttpResponse doConfigLogger(@QueryParameter String name, @QueryParameter String level) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        Level lv;
        if (level.equals("inherit"))
            lv = null;
        else
            lv = Level.parse(level.toUpperCase(Locale.ENGLISH));
        Logger target;
        if (Collections.list(LogManager.getLogManager().getLoggerNames()).contains(name)
                && (target = Logger.getLogger(name)) != null) {
            target.setLevel(lv);
            return new HttpRedirect("levels");
        } else {
            throw new Failure(Messages.LogRecorderManager_LoggerNotFound(name));
        }
    }

    /**
     * RSS feed for log entries.
     */
    public void doRss(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        doRss(req, rsp, Jenkins.logRecords);
    }

    /**
     * Renders the given log recorders as RSS.
     */
    /*package*/ static void doRss(StaplerRequest2 req, StaplerResponse2 rsp, List<LogRecord> logs) throws IOException, ServletException {
        // filter log records based on the log level
        String entryType = "all";
        String level = req.getParameter("level");
        if (level != null) {
            Level threshold = Level.parse(level);
            List<LogRecord> filtered = new ArrayList<>();
            for (LogRecord r : logs) {
                if (r.getLevel().intValue() >= threshold.intValue())
                    filtered.add(r);
            }
            logs = filtered;
            entryType = level;
        }

        RSS.forwardToRss("Jenkins:log (" + entryType + " entries)", "", logs, new FeedAdapter<>() {
            @Override
            public String getEntryTitle(LogRecord entry) {
                return entry.getMessage();
            }

            @Override
            public String getEntryUrl(LogRecord entry) {
                return "log";   // TODO: one URL for one log entry?
            }

            @Override
            public String getEntryID(LogRecord entry) {
                return String.valueOf(entry.getSequenceNumber());
            }

            @Override
            public String getEntryDescription(LogRecord entry) {
                return Functions.printLogRecord(entry);
            }

            @Override
            public Calendar getEntryTimestamp(LogRecord entry) {
                GregorianCalendar cal = new GregorianCalendar();
                cal.setTimeInMillis(entry.getMillis());
                return cal;
            }

            @Override
            public String getEntryAuthor(LogRecord entry) {
                return JenkinsLocationConfiguration.get().getAdminAddress();
            }
        }, req, rsp);
    }

    @Initializer(before = PLUGINS_PREPARED)
    public static void init(Jenkins h) throws IOException {
        h.getLog().load();
    }

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK) {
            Jenkins.get().checkPermission(Jenkins.SYSTEM_READ);
        }
        return this;
    }

    /**
     * Escape hatch for StaplerProxy-based access control
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(LogRecorderManager.class.getName() + ".skipPermissionCheck");
}
