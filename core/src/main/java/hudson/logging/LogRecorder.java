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

import com.google.common.annotations.VisibleForTesting;
import com.thoughtworks.xstream.XStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.Extension;
import hudson.FilePath;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.AbstractModelObject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Computer;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.listeners.SaveableListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;
import hudson.util.CopyOnWriteList;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.RingBufferLogHandler;
import hudson.util.XStream2;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.Loadable;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.MemoryReductionUtil;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

/**
 * Records a selected set of logs so that the system administrator
 * can diagnose a specific aspect of the system.
 *
 * TODO: still a work in progress.
 *
 * <p><strong>Access Control</strong>:
 * {@link LogRecorder} is only visible for administrators and system readers, and this access control happens at
 * {@link jenkins.model.Jenkins#getLog()}, the sole entry point for binding {@link LogRecorder} to URL.
 *
 * @author Kohsuke Kawaguchi
 * @see LogRecorderManager
 */
public class LogRecorder extends AbstractModelObject implements Loadable, Saveable {
    private volatile String name;

    /**
     * No longer used.
     *
     * @deprecated use {@link #getLoggers()}
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("2.324")
    public final transient CopyOnWriteList<Target> targets = new CopyOnWriteList<>();
    private List<Target> loggers = new ArrayList<>();
    private static final TargetComparator TARGET_COMPARATOR = new TargetComparator();

    @DataBoundConstructor
    public LogRecorder(String name) {
        this.name = name;
        // register it only once when constructed, and when this object dies
        // WeakLogHandler will remove it
        new WeakLogHandler(handler, Logger.getLogger(""));
    }

    private Object readResolve() {
        if (loggers == null) {
            loggers = new ArrayList<>();
        }

        List<Target> tempLoggers = new ArrayList<>(loggers);

        if (!targets.isEmpty()) {
            loggers.addAll(targets.getView());
        }
        if (!tempLoggers.isEmpty() && !targets.getView().equals(tempLoggers)) {
            targets.addAll(tempLoggers);
        }
        return this;
    }

    public List<Target> getLoggers() {
        return loggers;
    }

    public void setLoggers(List<Target> loggers) {
        this.loggers = loggers;
    }

    @Restricted(NoExternalUse.class)
    Target[] orderedTargets() {
        // will contain targets ordered by reverse name length (place specific targets at the beginning)
        Target[] ts = loggers.toArray(new Target[]{});

        Arrays.sort(ts, TARGET_COMPARATOR);

        return ts;
    }

    @Restricted(NoExternalUse.class)
    @VisibleForTesting
    public static Set<String> getAutoCompletionCandidates(List<String> loggerNamesList) {
        Set<String> loggerNames = new HashSet<>(loggerNamesList);

        // now look for package prefixes that make sense to offer for autocompletion:
        // Only prefixes that match multiple loggers will be shown.
        // Example: 'org' will show 'org', because there's org.apache, org.jenkinsci, etc.
        // 'io' might only show 'io.jenkins.plugins' rather than 'io' if all loggers starting with 'io' start with 'io.jenkins.plugins'.
        HashMap<String, Integer> seenPrefixes = new HashMap<>();
        SortedSet<String> relevantPrefixes = new TreeSet<>();
        for (String loggerName : loggerNames) {
            String[] loggerNameParts = loggerName.split("[.]");

            String longerPrefix = null;
            for (int i = loggerNameParts.length; i > 0; i--) {
                String loggerNamePrefix = String.join(".", Arrays.copyOf(loggerNameParts, i));
                seenPrefixes.put(loggerNamePrefix, seenPrefixes.getOrDefault(loggerNamePrefix, 0) + 1);
                if (longerPrefix == null) {
                    relevantPrefixes.add(loggerNamePrefix); // actual logger name
                    longerPrefix = loggerNamePrefix;
                    continue;
                }

                if (seenPrefixes.get(loggerNamePrefix) > seenPrefixes.get(longerPrefix)) {
                    relevantPrefixes.add(loggerNamePrefix);
                }
                longerPrefix = loggerNamePrefix;
            }
        }
        return relevantPrefixes;
    }

    /**
     * Validate the name.
     *
     * @return {@link FormValidation#ok} if the log target is not empty, otherwise {@link
     *     FormValidation#warning} with a message explaining the problem.
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    @VisibleForTesting
    public FormValidation doCheckName(@QueryParameter String value, @QueryParameter String level) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        try {
            if ((Util.fixEmpty(level) == null || Level.parse(level).intValue() <= Level.FINE.intValue())
                    && Util.fixEmpty(value) == null) {
                return FormValidation.warning(Messages.LogRecorder_Target_Empty_Warning());
            }
        } catch (IllegalArgumentException iae) {
            // We cannot figure out the level, if the name is empty show a warning
            if (Util.fixEmpty(value) == null) {
                return FormValidation.warning(Messages.LogRecorder_Target_Empty_Warning());
            }
        }
        return FormValidation.ok();
    }

    @Restricted(NoExternalUse.class)
    public AutoCompletionCandidates doAutoCompleteLoggerName(@QueryParameter String value) {
        if (value == null) {
            return new AutoCompletionCandidates();
        }

        // get names of all actual loggers known to Jenkins
        Set<String> candidateNames = new LinkedHashSet<>(getAutoCompletionCandidates(Collections.list(LogManager.getLogManager().getLoggerNames())));

        for (String part : value.split("[ ]+")) {
            HashSet<String> partCandidates = new HashSet<>();
            String lowercaseValue = part.toLowerCase(Locale.ENGLISH);
            for (String loggerName : candidateNames) {
                if (loggerName.toLowerCase(Locale.ENGLISH).contains(lowercaseValue)) {
                    partCandidates.add(loggerName);
                }
            }
            candidateNames.retainAll(partCandidates);
        }
        AutoCompletionCandidates candidates = new AutoCompletionCandidates();
        candidates.add(candidateNames.toArray(MemoryReductionUtil.EMPTY_STRING_ARRAY));
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

                if (match) {
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
            this(name, level.intValue());
        }

        public Target(String name, int level) {
            this.name = name;
            this.level = level;
        }

        @DataBoundConstructor
        public Target(String name, String level) {
            this(name, Level.parse(level));
        }

        public Level getLevel() {
            return Level.parse(String.valueOf(level));
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Target target = (Target) o;
            return level == target.level && Objects.equals(name, target.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, level);
        }

        @Deprecated
        public boolean includes(LogRecord r) {
            if (r.getLevel().intValue() < level)
                return false;   // below the threshold
            if (name.isEmpty()) {
                return true; // like root logger, includes everything
            }
            String logName = r.getLoggerName();
            if (logName == null || !logName.startsWith(name))
                return false;   // not within this logger
            String rest = logName.substring(name.length());
            return rest.startsWith(".") || rest.isEmpty();
        }

        @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "converting this to YesNoMaybe would break backward compatibility")
        public Boolean matches(LogRecord r) {
            boolean levelSufficient = r.getLevel().intValue() >= level;
            if (name.isEmpty()) {
                return levelSufficient; // include if level matches
            }
            String logName = r.getLoggerName();
            if (logName == null || !logName.startsWith(name))
                return null; // not in the domain of this logger
            String rest = logName.substring(name.length());
            if (rest.startsWith(".") || rest.isEmpty()) {
                return levelSufficient; // include if level matches
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
            if (!l.isLoggable(getLevel()))
                l.setLevel(getLevel());
            new SetLevel(name, getLevel()).broadcast();
        }

        public void disable() {
            getLogger().setLevel(null);
            new SetLevel(name, null).broadcast();
        }

    }

    private static class TargetComparator implements Comparator<Target>, Serializable {

        private static final long serialVersionUID = 9285340752515798L;

        @Override
        public int compare(Target left, Target right) {
            return right.getName().length() - left.getName().length();
        }
    }

    private static final class SetLevel extends MasterToSlaveCallable<Void, Error> {
        /** known loggers (kept per agent), to avoid GC */
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") private static final Set<Logger> loggers = new HashSet<>();
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
            for (Computer c : Jenkins.get().getComputers()) {
                if (!c.getName().isEmpty()) { // i.e. not master
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
            for (LogRecorder recorder : Jenkins.get().getLog().getRecorders()) {
                for (Target t : recorder.getLoggers()) {
                    channel.call(new SetLevel(t.name, t.getLevel()));
                }
            }
        }
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public String getSearchUrl() {
        return Util.rawEncode(name);
    }

    public String getName() {
        return name;
    }

    public LogRecorderManager getParent() {
        return Jenkins.get().getLog();
    }

    /**
     * Accepts submission from the configuration page.
     */
    @POST
    public synchronized void doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JSONObject src = req.getSubmittedForm();

        String newName = src.getString("name"), redirect = ".";
        XmlFile oldFile = null;
        if (!name.equals(newName)) {
            Jenkins.checkGoodName(newName);
            oldFile = getConfigFile();
            // rename
            List<LogRecorder> recorders = getParent().getRecorders();
            recorders.remove(new LogRecorder(name));
            this.name = newName;
            recorders.add(this);
            getParent().setRecorders(recorders); // ensure that legacy logRecorders field is synced on save
            redirect = "../" + Util.rawEncode(newName) + '/';
        }

        List<Target> newTargets = req.bindJSONToList(Target.class, src.get("loggers"));
        setLoggers(newTargets);

        save();
        if (oldFile != null) oldFile.delete();
        FormApply.success(redirect).generateResponse(req, rsp, null);
    }

    @RequirePOST
    public HttpResponse doClear() throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        handler.clear();
        return HttpResponses.redirectToDot();
    }

    /**
     * Loads the settings from a file.
     */
    @Override
    public synchronized void load() throws IOException {
        getConfigFile().unmarshal(this);
        loggers.forEach(Target::enable);
    }

    /**
     * Save the settings to a file.
     */
    @Override
    public synchronized void save() throws IOException {
        if (BulkChange.contains(this))   return;

        handlePluginUpdatingLegacyLogManagerMap();
        getConfigFile().write(this);
        loggers.forEach(Target::enable);

        SaveableListener.fireOnChange(this, getConfigFile());
    }

    @SuppressWarnings("deprecation") // this is for compatibility
    private void handlePluginUpdatingLegacyLogManagerMap() {
        if (getParent().logRecorders.size() > getParent().getRecorders().size()) {
            for (LogRecorder logRecorder : getParent().logRecorders.values()) {
                if (!getParent().getRecorders().contains(logRecorder)) {
                    getParent().getRecorders().add(logRecorder);
                }
            }
        }
        if (getParent().getRecorders().size() > getParent().logRecorders.size()) {
            for (LogRecorder logRecorder : getParent().getRecorders()) {
                if (!getParent().logRecorders.containsKey(logRecorder.getName())) {
                    getParent().logRecorders.put(logRecorder.getName(), logRecorder);
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LogRecorder that = (LogRecorder) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * Deletes this recorder, then go back to the parent.
     */
    @RequirePOST
    public synchronized void doDoDelete(StaplerResponse2 rsp) throws IOException, ServletException {
        delete();
        rsp.sendRedirect2("..");
    }

    /**
     * Deletes this log recorder.
     * @throws IOException In case anything went wrong while deleting the configuration file.
     * @since 2.425
     */
    public void delete() throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        getConfigFile().delete();
        getParent().getRecorders().remove(new LogRecorder(name));
        // Disable logging for all our targets,
        // then reenable all other loggers in case any also log the same targets
        loggers.forEach(Target::disable);

        getParent().getRecorders().forEach(logRecorder -> logRecorder.getLoggers().forEach(Target::enable));
        SaveableListener.fireOnDeleted(this, getConfigFile());
    }

    /**
     * RSS feed for log entries.
     */
    public void doRss(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        LogRecorderManager.doRss(req, rsp, getLogRecords());
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
    public Map<Computer, List<LogRecord>> getSlaveLogRecords() {
        Map<Computer, List<LogRecord>> result = new TreeMap<>(new Comparator<>() {
            final Collator COLL = Collator.getInstance();

            @Override
            public int compare(Computer c1, Computer c2) {
                return COLL.compare(c1.getDisplayName(), c2.getDisplayName());
            }
        });
        for (Computer c : Jenkins.get().getComputers()) {
            if (c.getName().isEmpty()) {
                continue; // master
            }
            List<LogRecord> recs = new ArrayList<>();
            try {
                for (LogRecord rec : c.getLogRecords()) {
                    for (Target t : loggers) {
                        if (t.includes(rec)) {
                            recs.add(rec);
                            break;
                        }
                    }
                }
            } catch (IOException | InterruptedException x) {
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
        XSTREAM.alias("log", LogRecorder.class);
        XSTREAM.alias("target", Target.class);
    }

    /**
     * Log levels that can be configured for {@link Target}.
     */
    public static List<Level> LEVELS =
            Arrays.asList(Level.ALL, Level.FINEST, Level.FINER, Level.FINE, Level.CONFIG, Level.INFO, Level.WARNING, Level.SEVERE, Level.OFF);
}
