package jenkins.util.io;

import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uses a presence/absence of a file as a persisted boolean storage.
 *
 * <p>
 * This is convenient when you need to store just a few bits of infrequently accessed information
 * as you can forget the explicit persistence of it. This class masks I/O problem, so if the persistence
 * fails, you'll get no error report.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.498
 */
public class FileBoolean {
    private final File file;
    private volatile Boolean state;

    public FileBoolean(File file) {
        this.file = file;
    }

    public FileBoolean(Class owner, String name) {
        this(new File(Jenkins.getInstance().getRootDir(),owner.getName().replace('$','.')+'/'+name));
    }

    /**
     * Gets the current state. True if the file exists, false if it doesn't.
     */
    public boolean get() {
        return state=file.exists();
    }

    /**
     * Like {@link #get()} except instead of checking the actual file, use the result from the last {@link #get()} call.
     */
    public boolean fastGet() {
        if (state==null)    return get();
        return state;
    }

    public boolean isOn() { return get(); }
    public boolean isOff() { return !get(); }

    public void set(boolean b) {
        if (b) on(); else off();
    }

    public void on() {
        try {
            file.getParentFile().mkdirs();
            new FileOutputStream(file).close();
            get();  // update state
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to touch "+file);
        }
    }

    public void off() {
        file.delete();
        get();  // update state
    }

    private static final Logger LOGGER = Logger.getLogger(FileBoolean.class.getName());
}
