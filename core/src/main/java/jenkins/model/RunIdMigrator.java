/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.model;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.AtomicFileWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Converts legacy {@code builds} directories to the current format.
 *
 * There would be one instance associated with each {@link Job}, to retain ID → build# mapping.
 *
 * The {@link Job#getBuildDir} is passed to every method call (rather than being cached) in case it is moved.
 */
@Restricted(NoExternalUse.class)
public final class RunIdMigrator {

    private final DateFormat legacyIdFormatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    static final Logger LOGGER = Logger.getLogger(RunIdMigrator.class.getName());
    private static final String MAP_FILE = "legacyIds";
    /** avoids wasting a map for new jobs */
    private static final Map<String, Integer> EMPTY = new TreeMap<>();

    private @NonNull Map<String, Integer> idToNumber = EMPTY;

    public RunIdMigrator() {}

    /**
     * @return whether there was a file to load
     */
    private boolean load(File dir) {
        File f = new File(dir, MAP_FILE);
        if (!f.isFile()) {
            return false;
        }
        if (f.length() == 0) {
            return true;
        }
        idToNumber = new TreeMap<>();
        try {
            for (String line : Files.readAllLines(Util.fileToPath(f), StandardCharsets.UTF_8)) {
                int i = line.indexOf(' ');
                idToNumber.put(line.substring(0, i), Integer.parseInt(line.substring(i + 1)));
            }
        } catch (Exception x) { // IOException, IndexOutOfBoundsException, NumberFormatException
            LOGGER.log(WARNING, "could not read from " + f, x);
        }
        return true;
    }

    private void save(File dir) {
        File f = new File(dir, MAP_FILE);
        try (AtomicFileWriter w = new AtomicFileWriter(f)) {
            try {
                synchronized (this) {
                    for (Map.Entry<String, Integer> entry : idToNumber.entrySet()) {
                        w.write(entry.getKey() + ' ' + entry.getValue() + '\n');
                    }
                }
                w.commit();
            } finally {
                w.abort();
            }
        } catch (IOException x) {
            LOGGER.log(WARNING, "could not save changes to " + f, x);
        }
    }

    /**
     * Called when a job is first created.
     * Just saves an empty marker indicating that this job needs no migration.
     * @param dir as in {@link Job#getBuildDir}
     */
    public void created(File dir) {
        save(dir);
    }

    /**
     * Perform one-time migration if this has not been done already.
     * Where previously there would be a {@code 2014-01-02_03-04-05/build.xml} specifying {@code <number>99</number>} plus a symlink {@code 99 → 2014-01-02_03-04-05},
     * after migration there will be just {@code 99/build.xml} specifying {@code <id>2014-01-02_03-04-05</id>} and {@code <timestamp>…</timestamp>} according to local time zone at time of migration.
     * Newly created builds are untouched.
     * Does not throw {@link IOException} since we make a best effort to migrate but do not consider it fatal to job loading if we cannot.
     * @param dir as in {@link Job#getBuildDir}
     * @param jenkinsHome root directory of Jenkins (for logging only)
     * @return true if migration was performed
     */
    public synchronized boolean migrate(File dir, @CheckForNull File jenkinsHome) {
        if (load(dir)) {
            LOGGER.log(FINER, "migration already performed for {0}", dir);
            return false;
        }
        if (!dir.isDirectory()) {
            LOGGER.log(/* normal during Job.movedTo */FINE, "{0} was unexpectedly missing", dir);
            return false;
        }
        LOGGER.log(INFO, "Migrating build records in {0}. See https://www.jenkins.io/redirect/build-record-migration for more information.", dir);
        doMigrate(dir);
        save(dir);
        return true;
    }

    private static final Pattern NUMBER_ELT = Pattern.compile("(?m)^  <number>(\\d+)</number>(\r?\n)");

    private void doMigrate(File dir) {
        idToNumber = new TreeMap<>();
        File[] kids = dir.listFiles();
        // Need to process symlinks first so we can rename to them.
        List<File> kidsList = new ArrayList<>(Arrays.asList(kids));
        Iterator<File> it = kidsList.iterator();
        while (it.hasNext()) {
            File kid = it.next();
            String name = kid.getName();
            try {
                Integer.parseInt(name);
            } catch (NumberFormatException x) {
                LOGGER.log(FINE, "ignoring nonnumeric entry {0}", name);
                continue;
            }
            try {
                if (Util.isSymlink(kid)) {
                    LOGGER.log(FINE, "deleting build number symlink {0} → {1}", new Object[] {name, Util.resolveSymlink(kid)});
                } else if (kid.isDirectory()) {
                    LOGGER.log(FINE, "ignoring build directory {0}", name);
                    continue;
                } else {
                    LOGGER.log(WARNING, "need to delete anomalous file entry {0}", name);
                }
                Util.deleteFile(kid);
                it.remove();
            } catch (Exception x) {
                LOGGER.log(WARNING, "failed to process " + kid, x);
            }
        }
        it = kidsList.iterator();
        while (it.hasNext()) {
            File kid = it.next();
            try {
                String name = kid.getName();
                try {
                    Integer.parseInt(name);
                    LOGGER.log(FINE, "skipping new build dir {0}", name);
                    continue;
                } catch (NumberFormatException x) {
                    // OK, next…
                }
                if (!kid.isDirectory()) {
                    LOGGER.log(FINE, "skipping non-directory {0}", name);
                    continue;
                }
                long timestamp;
                try {
                    synchronized (legacyIdFormatter) {
                        timestamp = legacyIdFormatter.parse(name).getTime();
                    }
                } catch (ParseException x) {
                    LOGGER.log(WARNING, "found unexpected dir {0}", name);
                    continue;
                }
                File buildXml = new File(kid, "build.xml");
                if (!buildXml.isFile()) {
                    LOGGER.log(WARNING, "found no build.xml in {0}", name);
                    continue;
                }
                String xml = Files.readString(Util.fileToPath(buildXml), StandardCharsets.UTF_8);
                Matcher m = NUMBER_ELT.matcher(xml);
                if (!m.find()) {
                    LOGGER.log(WARNING, "could not find <number> in {0}/build.xml", name);
                    continue;
                }
                int number = Integer.parseInt(m.group(1));
                String nl = m.group(2);
                xml = m.replaceFirst("  <id>" + name + "</id>" + nl + "  <timestamp>" + timestamp + "</timestamp>" + nl);
                File newKid = new File(dir, Integer.toString(number));
                move(kid, newKid);
                Files.writeString(Util.fileToPath(newKid).resolve("build.xml"), xml, StandardCharsets.UTF_8);
                LOGGER.log(FINE, "fully processed {0} → {1}", new Object[] {name, number});
                idToNumber.put(name, number);
            } catch (Exception x) {
                LOGGER.log(WARNING, "failed to process " + kid, x);
            }
        }
    }

    /**
     * Tries to move/rename a file from one path to another.
     * Uses {@link java.nio.file.Files#move} when available.
     * Does not use {@link java.nio.file.StandardCopyOption#REPLACE_EXISTING} or any other options.
     * TODO candidate for moving to {@link Util}
     */
    static void move(File src, File dest) throws IOException {
        try {
            Files.move(src.toPath(), dest.toPath());
        } catch (IOException x) {
            throw x;
        } catch (RuntimeException x) {
            throw new IOException(x);
        }
    }

    /**
     * Look up a historical run by ID.
     * @param id a nonnumeric ID which may be a valid {@link Run#getId}
     * @return the corresponding {@link Run#number}, or 0 if unknown
     */
    public synchronized int findNumber(@NonNull String id) {
        Integer number = idToNumber.get(id);
        return number != null ? number : 0;
    }

    /**
     * Delete the record of a build.
     * @param dir as in {@link Job#getBuildDir}
     * @param id a {@link Run#getId}
     */
    public synchronized void delete(File dir, String id) {
        if (idToNumber.remove(id) != null) {
            save(dir);
        }
    }
}
