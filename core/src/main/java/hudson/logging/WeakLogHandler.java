package hudson.logging;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Delegating {@link Handler} that uses {@link WeakReference},
 * which de-registers itself when an object disappears via GC.
 */
public final class WeakLogHandler extends Handler {
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
