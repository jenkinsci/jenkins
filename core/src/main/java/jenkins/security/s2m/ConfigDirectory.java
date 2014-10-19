package jenkins.security.s2m;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bit of a hack to expand {@link ConfigFile} to support conf.d format that assembles the fragment.
 *
 * <p>
 * {@link #file} points to the "primary" file that we programmatically write to.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class ConfigDirectory<T,COL extends Collection<T>> extends ConfigFile<T,COL> {
    private final File dir;

    protected ConfigDirectory(File file) {
        super(file);
        this.dir = file.getParentFile();
    }

    @Override
    public synchronized void load() {
        COL result = create();

        if (dir.exists()) {
            String[] fragments = dir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".conf");
                }
            });
            if (fragments!=null) {
                Arrays.sort(fragments);

                for (String fragment : fragments) {
                    File f = new File(dir, fragment);
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(f));
                        String line;
                        while ((line=reader.readLine())!=null) {
                            if (line.startsWith("#")) continue;   // comment
                            T r = parse(line);
                            if (r != null)
                                result.add(r);
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to parse "+f,e);
                    }
                }
            }
        }

        parsed = readOnly(result);
    }

    private static final Logger LOGGER = Logger.getLogger(ConfigDirectory.class.getName());
}
