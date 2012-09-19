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

import com.thoughtworks.xstream.XStream;
import hudson.BulkChange;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.AbstractModelObject;
import jenkins.model.Jenkins;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.CopyOnWriteList;
import hudson.util.RingBufferLogHandler;
import hudson.util.XStream2;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Records a selected set of logs so that the system administrator
 * can diagnose a specific aspect of the system.
 *
 * TODO: still a work in progress.
 *
 * <h3>Access Control</h3>
 * {@link LogRecorder} is only visible for administrators, and this access control happens at
 * {@link jenkins.model.Jenkins#getLog()}, the sole entry point for binding {@link LogRecorder} to URL.
 *
 * @author Kohsuke Kawaguchi
 * @see LogRecorderManager
 */
public class LogRecorder extends AbstractModelObject implements Saveable {
    private volatile String name;

    public final CopyOnWriteList<Target> targets = new CopyOnWriteList<Target>();

    private transient /*almost final*/ RingBufferLogHandler handler = new RingBufferLogHandler() {
        @Override
        public void publish(LogRecord record) {
            for (Target t : targets) {
                if(t.includes(record)) {
                    super.publish(record);
                    return;
                }
            }
        }
    };

    /**
     * Logger that this recorder monitors, and its log level.
     * Just a pair of (logger name,level) with convenience methods.
     */
    public static final class Target {
        public final String name;
        private final int level;
        private transient /* almost final*/ Logger logger;

        public Target(String name, Level level) {
            this(name,level.intValue());
        }

        public Target(String name, int level) {
            this.name = name;
            this.level = level;
        }

        @DataBoundConstructor
        public Target(String name, String level) {
            this(name,Level.parse(level.toUpperCase(Locale.ENGLISH)));
        }

        public Level getLevel() {
            return Level.parse(String.valueOf(level));
        }

        public boolean includes(LogRecord r) {
            if(r.getLevel().intValue() < level)
                return false;   // below the threshold
            String logName = r.getLoggerName();
            if(logName==null || !logName.startsWith(name))
                return false;   // not within this logger

            String rest = r.getLoggerName().substring(name.length());
            return rest.startsWith(".") || rest.length()==0;
        }

        public Logger getLogger() {
            if (logger == null) {
                logger = Logger.getLogger(name);
            }
            return logger;
        }

        /**
         * Makes sure that the logger passes through messages at the correct level to us.
         */
        public void enable() {
            Logger l = getLogger();
            if(!l.isLoggable(getLevel()))
                l.setLevel(getLevel());
        }
    }

    public LogRecorder(String name) {
        this.name = name;
        // register it only once when constructed, and when this object dies
        // WeakLogHandler will remove it
        new WeakLogHandler(handler,Logger.getLogger(""));
    }

    public String getDisplayName() {
        return name;
    }

    public String getSearchUrl() {
        return name;
    }

    public String getName() {
        return name;
    }

    public LogRecorderManager getParent() {
        return Jenkins.getInstance().getLog();
    }

    /**
     * Accepts submission from the configuration page.
     */
    @RequirePOST
    public synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        JSONObject src = req.getSubmittedForm();

        String newName = src.getString("name"), redirect = ".";
        XmlFile oldFile = null;
        if(!name.equals(newName)) {
            Jenkins.checkGoodName(newName);
            oldFile = getConfigFile();
            // rename
            getParent().logRecorders.remove(name);
            this.name = newName;
            getParent().logRecorders.put(name,this);
            redirect = "../" + Util.rawEncode(newName) + '/';
        }

        List<Target> newTargets = req.bindJSONToList(Target.class, src.get("targets"));
        for (Target t : newTargets)
            t.enable();
        targets.replaceBy(newTargets);

        save();
        if (oldFile!=null) oldFile.delete();
        rsp.sendRedirect2(redirect);
    }

    /**
     * Loads the settings from a file.
     */
    public synchronized void load() throws IOException {
        getConfigFile().unmarshal(this);
        for (Target t : targets)
            t.enable();
    }

    /**
     * Save the settings to a file.
     */
    public synchronized void save() throws IOException {
        if(BulkChange.contains(this))   return;
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    /**
     * Deletes this recorder, then go back to the parent.
     */
    @RequirePOST
    public synchronized void doDoDelete(StaplerResponse rsp) throws IOException, ServletException {
        getConfigFile().delete();
        getParent().logRecorders.remove(name);
        // Disable logging for all our targets,
        // then reenable all other loggers in case any also log the same targets
        for (Target t : targets)
            t.getLogger().setLevel(null);
        for (LogRecorder log : getParent().logRecorders.values())
            for (Target t : log.targets)
                t.enable();
        rsp.sendRedirect2("..");
    }

    /**
     * RSS feed for log entries.
     */
    public void doRss( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        LogRecorderManager.doRss(req,rsp,getLogRecords());
    }

    /**
     * The file we save our configuration.
     */
    private XmlFile getConfigFile() {
        return new XmlFile(XSTREAM, new File(Jenkins.getInstance().getRootDir(),"log/"+name+".xml"));
    }

    /**
     * Gets a view of the log records.
     */
    public List<LogRecord> getLogRecords() {
        return handler.getView();
    }

    /**
     * Thread-safe reusable {@link XStream}.
     */
    public static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("log",LogRecorder.class);
        XSTREAM.alias("target",Target.class);
    }

    /**
     * Log levels that can be configured for {@link Target}.
     */
    public static List<Level> LEVELS =
            Arrays.asList(Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG,
                    Level.FINE, Level.FINER, Level.FINEST, Level.ALL);
}
