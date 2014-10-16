package jenkins.security.admin;

import hudson.CopyOnWrite;
import hudson.util.TextFile;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Abstraction of a line-by-line configuration text file that gets parsed into some in-memory data form.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class ConfigFile<T,COL extends Collection<T>> extends TextFile {
    @CopyOnWrite
    private volatile COL parsed;

    public ConfigFile(File file) {
        super(file);
        load();
    }

    protected abstract COL create();
    protected abstract COL readOnly(COL base);

    public synchronized void load() {
        COL result = create();

        if (exists()) {
            for (String line : lines()) {
                if (line.startsWith("#")) continue;   // comment
                T r = parse(line);
                if (r != null)
                    result.add(r);
            }
        }

        parsed = readOnly(result);
    }

    protected abstract T parse(String line);

    public synchronized void set(String newContent) throws IOException {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        write(newContent);
        load();
    }

    public synchronized void append(String additional) throws IOException {
        String s = read();
        if (!s.endsWith("\n"))
            s += "\n";
        s+= additional;

        set(s);
    }

    public COL get() {
        // load upon the first use
        if (parsed==null) {
            synchronized (this) {
                if (parsed==null)
                    load();
            }
        }
        return parsed;
    }


}
