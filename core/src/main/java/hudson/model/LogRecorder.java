package hudson.model;

import hudson.util.RingBufferLogHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Handler;
import java.util.logging.Formatter;
import java.util.logging.Filter;
import java.util.logging.ErrorManager;
import java.util.logging.Logger;
import java.lang.ref.WeakReference;
import java.io.UnsupportedEncodingException;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Records a selected set of logs so that the system administrator
 * can diagnose a specific aspect of the system.
 *
 * TODO: still a work in progress.
 *
 * @author Kohsuke Kawaguchi
 */
public class LogRecorder extends AbstractModelObject {
    private volatile String name;

    private final List<Target> targets = new CopyOnWriteArrayList<Target>();

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
     * Logger that this recorder monitors.
     */
    public static final class Target {
        public final String name;
        private final int level;

        public Target(String name, Level level) {
            this(name,level.intValue());
        }

        @DataBoundConstructor
        public Target(String name, int level) {
            this.name = name;
            this.level = level;
        }

        public Level getLevel() {
            return Level.parse(String.valueOf(level));
        }

        public boolean includes(LogRecord r) {
            if(r.getLevel().intValue() < level)
                return false;   // below the threshold
            if(!r.getLoggerName().startsWith(name))
                return false;   // not within this logger

            String rest = r.getLoggerName().substring(name.length());
            return rest.startsWith(".") || rest.length()==0;
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

    public void doConfigure() {
        // TODO


    }

    /**
     * Delegating {@link Handler} that uses {@link WeakReference},
     * which de-registers itself when an object disappears via GC.
     */
    public static final class WeakLogHandler extends Handler {
        private final WeakReference<Handler> target;
        private final Logger logger;

        public WeakLogHandler(Handler target, Logger logger) {
            this.logger = logger;
            logger.addHandler(this);
            this.target = new WeakReference<Handler>(target);
        }

        public void publish(LogRecord record) {
            Handler t = resolve();
            if(t!=null)
                t.publish(record);
        }

        public void flush() {
            Handler t = resolve();
            if(t!=null)
                t.flush();
        }

        public void close() throws SecurityException {
            Handler t = resolve();
            if(t!=null)
                t.close();
        }

        private Handler resolve() {
            Handler r = target.get();
            if(r==null)
                logger.removeHandler(this);
            return r;
        }

        @Override
        public void setFormatter(Formatter newFormatter) throws SecurityException {
            super.setFormatter(newFormatter);
            Handler t = resolve();
            if(t!=null)
                t.setFormatter(newFormatter);
        }

        @Override
        public void setEncoding(String encoding) throws SecurityException, UnsupportedEncodingException {
            super.setEncoding(encoding);
            Handler t = resolve();
            if(t!=null)
                t.setEncoding(encoding);
        }

        @Override
        public void setFilter(Filter newFilter) throws SecurityException {
            super.setFilter(newFilter);
            Handler t = resolve();
            if(t!=null)
                t.setFilter(newFilter);
        }

        @Override
        public void setErrorManager(ErrorManager em) {
            super.setErrorManager(em);
            Handler t = resolve();
            if(t!=null)
                t.setErrorManager(em);
        }

        @Override
        public void setLevel(Level newLevel) throws SecurityException {
            super.setLevel(newLevel);
            Handler t = resolve();
            if(t!=null)
                t.setLevel(newLevel);
        }

        @Override
        public boolean isLoggable(LogRecord record) {
            Handler t = resolve();
            if(t!=null)
                return t.isLoggable(record);
            else
                return super.isLoggable(record);
        }
    }
}
