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
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.*;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import hudson.model.listeners.SaveableListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import hudson.util.CopyOnWriteList;
import hudson.util.RingBufferLogHandler;
import hudson.util.XStream2;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

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

    @Restricted(NoExternalUse.class)
    Target[] orderedTargets() {
        // will contain targets ordered by reverse name length (place specific targets at the beginning)
        Target[] ts = targets.toArray(new Target[]{});

        Arrays.sort(ts, new Comparator<Target>() {
            public int compare(Target left, Target right) {
                return right.getName().length() - left.getName().length();
            }
        });

        return ts;
    }

    @Restricted(NoExternalUse.class)
    public AutoCompletionCandidates doAutoCompleteLoggerName(@QueryParameter String value) {
        AutoCompletionCandidates candidates = new AutoCompletionCandidates();
        Enumeration<String> loggerNames = LogManager.getLogManager().getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            String loggerName = loggerNames.nextElement();
            if (loggerName.toLowerCase(Locale.ENGLISH).contains(value.toLowerCase(Locale.ENGLISH))) {
                candidates.add(loggerName);
            }
        }
        return candidates;
    }

    @Restricted(NoExternalUse.class)
    transient /*almost final*/ RingBufferLogHandler handler = new RingBufferLogHandler() {
        @Override
        public void publish(LogRecord record) {
            for (Target t : orderedTargets()) {
                Boolean match = t.matches(record);
                if (match == null) {
                    // domain does not match, so continue looking
                    continue;
                }

                if (match.booleanValue()) {
                    // most specific logger matches, so publish
                    super.publish(record);
                }
                // most specific logger does not match, so don't publish
                // allows reducing log level for more specific loggers
                return;
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
            this(name,Level.parse(level));
        }

        public Level getLevel() {
            return Level.parse(String.valueOf(level));
        }

        public String getName() {
            return name;
        }

        @Deprecated
        public boolean includes(LogRecord r) {
            if(r.getLevel().intValue() < level)
                return false;   // below the threshold
            if (name.length() == 0) {
                return true; // like root logger, includes everything
            }
            String logName = r.getLoggerName();
            if(logName==null || !logName.startsWith(name))
                return false;   // not within this logger
            String rest = logName.substring(name.length());
            return rest.startsWith(".") || rest.length()==0;
        }

        public Boolean matches(LogRecord r) {
            boolean levelSufficient = r.getLevel().intValue() >= level;
            if (name.length() == 0) {
                return Boolean.valueOf(levelSufficient); // include if level matches
            }
            String logName = r.getLoggerName();
            if(logName==null || !logName.startsWith(name))
                return null; // not in the domain of this logger
            String rest = logName.substring(name.length());
            if (rest.startsWith(".") || rest.length()==0) {
                return Boolean.valueOf(levelSufficient); // include if level matches
            }
            return null;
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
            new SetLevel(name, getLevel()).broadcast();
        }

        public void disable() {
            getLogger().setLevel(null);
            new SetLevel(name, null).broadcast();
        }

    }

    private static final class SetLevel extends MasterToSlaveCallable<Void,Error> {
        /** known loggers (kept per agent), to avoid GC */
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") private static final Set<Logger> loggers = new HashSet<Logger>();
        private final String name;
        private final Level level;
        SetLevel(String name, Level level) {
            this.name = name;
            this.level = level;
        }
        @Override public Void call() throws Error {
            Logger logger = Logger.getLogger(name);
            loggers.add(logger);
            logger.setLevel(level);
            return null;
        }
        void broadcast() {
            for (Computer c : Jenkins.getInstance().getComputers()) {
                if (c.getName().length() > 0) { // i.e. not master
                    VirtualChannel ch = c.getChannel();
                    if (ch != null) {
                        try {
                            ch.call(this);
                        } catch (Exception x) {
                            Logger.getLogger(LogRecorder.class.getName()).log(Level.WARNING, "could not set up logging on " + c, x);
                        }
                    }
                }
            }
        }
    }

    @Extension @Restricted(NoExternalUse.class) public static final class ComputerLogInitializer extends ComputerListener {
        @Override public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
            for (LogRecorder recorder : Jenkins.getInstance().getLog().logRecorders.values()) {
                for (Target t : recorder.targets) {
                    channel.call(new SetLevel(t.name, t.getLevel()));
                }
            }
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
        return Util.rawEncode(name);
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

    @RequirePOST
    public HttpResponse doClear() throws IOException {
        handler.clear();
        return HttpResponses.redirectToDot();
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
            t.disable();
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
        return new XmlFile(XSTREAM, new File(LogRecorderManager.configDir(), name + ".xml"));
    }

    /**
     * Gets a view of the log records.
     */
    public List<LogRecord> getLogRecords() {
        return handler.getView();
    }

    /**
     * Gets a view of log records per agent matching this recorder.
     * @return a map (sorted by display name) from computer to (nonempty) list of log records
     * @since 1.519
     */
    public Map<Computer,List<LogRecord>> getSlaveLogRecords() {
        Map<Computer,List<LogRecord>> result = new TreeMap<Computer,List<LogRecord>>(new Comparator<Computer>() {
            final Collator COLL = Collator.getInstance();
            public int compare(Computer c1, Computer c2) {
                return COLL.compare(c1.getDisplayName(), c2.getDisplayName());
            }
        });
        for (Computer c : Jenkins.getInstance().getComputers()) {
            if (c.getName().length() == 0) {
                continue; // master
            }
            List<LogRecord> recs = new ArrayList<LogRecord>();
            try {
                for (LogRecord rec : c.getLogRecords()) {
                    for (Target t : targets) {
                        if (t.includes(rec)) {
                            recs.add(rec);
                            break;
                        }
                    }
                }
            } catch (IOException x) {
                continue;
            } catch (InterruptedException x) {
                continue;
            }
            if (!recs.isEmpty()) {
                result.put(c, recs);
            }
        }
        return result;
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
            Arrays.asList(Level.ALL, Level.FINEST, Level.FINER, Level.FINE, Level.CONFIG, Level.INFO, Level.WARNING, Level.SEVERE);
}
