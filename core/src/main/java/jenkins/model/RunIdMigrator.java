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

import hudson.Util;
import hudson.model.Job;
import hudson.model.Run;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Converts legacy {@code builds} directories to the current format.
 * There would be one instance associated with each {@link Job}.
 * The {@link Job#getBuildDir} is passed to every method call (rather than being cached) in case it is moved.
 */
@Restricted(NoExternalUse.class)
public final class RunIdMigrator {

    static final Logger LOGGER = Logger.getLogger(RunIdMigrator.class.getName());
    private static final SimpleDateFormat LEGACY_ID_FORMATTER = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    private static final String MAP_FILE = "legacyIds";
    /** avoids wasting a map for new jobs */
    private static final Map<String,Integer> EMPTY = new TreeMap<String,Integer>();

    private @Nonnull Map<String,Integer> idToNumber = EMPTY;

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
        idToNumber = new TreeMap<String,Integer>();
        try {
            for (String line : FileUtils.readLines(f)) {
                int i = line.indexOf(' ');
                idToNumber.put(line.substring(0, i), Integer.parseInt(line.substring(i + 1)));
            }
        } catch (Exception x) { // IOException, IndexOutOfBoundsException, NumberFormatException
            LOGGER.log(Level.WARNING, "could not read from " + f, x);
        }
        return true;
    }

    private void save(File dir) {
        dir.mkdirs();
        File f = new File(dir, MAP_FILE);
        try {
            PrintWriter w = new PrintWriter(f);
            try {
                for (Map.Entry<String,Integer> entry : idToNumber.entrySet()) {
                    w.println(entry.getKey() + ' ' + entry.getValue());
                }
            } finally {
                w.close();
            }
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, "could not save changes to " + f, x);
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
     * @return true if migration was performed
     */
    public synchronized boolean migrate(File dir) {
        if (load(dir)) {
            LOGGER.log(Level.FINER, "migration already performed for {0}", dir);
            return false;
        }
        LOGGER.log(Level.INFO, "Migrating build records in {0}", dir);
        doMigrate(dir);
        save(dir);
        return true;
    }

    private static final Pattern NUMBER_ELT = Pattern.compile("(?m)^  <number>(\\d+)</number>(\r?\n)");
    private void doMigrate(File dir) {
        idToNumber = new TreeMap<String,Integer>();
        File[] kids = dir.listFiles();
        if (kids == null) {
            LOGGER.warning("dir was unexpectedly missing");
            return;
        }
        // Need to process symlinks first so we can rename to them.
        List<File> kidsList = new ArrayList<File>(Arrays.asList(kids));
        Iterator<File> it = kidsList.iterator();
        while (it.hasNext()) {
            File kid = it.next();
            String name = kid.getName();
            try {
                String link = Util.resolveSymlink(kid);
                if (link == null) {
                    continue;
                }
                try {
                    Integer.parseInt(name);
                    if (kid.delete()) {
                        LOGGER.log(Level.FINE, "deleted build number symlink {0} → {1}", new Object[] {name, link});
                    } else {
                        LOGGER.log(Level.WARNING, "could not delete build number symlink {0} → {1}", new Object[] {name, link});
                    }
                } catch (NumberFormatException x) {
                    LOGGER.log(Level.FINE, "skipping other symlink {0} → {1}", new Object[] {name, link});
                }
                it.remove();
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, "failed to process " + kid, x);
            }
        }
        it = kidsList.iterator();
        while (it.hasNext()) {
            File kid = it.next();
            try {
                String name = kid.getName();
                try {
                    Integer.parseInt(name);
                    LOGGER.log(Level.FINE, "skipping new build dir {0}", name);
                    continue;
                } catch (NumberFormatException x) {
                    // OK, next…
                }
                if (!kid.isDirectory()) {
                    LOGGER.log(Level.FINE, "skipping non-directory {0}", name);
                    continue;
                }
                long timestamp;
                try {
                    timestamp = LEGACY_ID_FORMATTER.parse(name).getTime();
                } catch (ParseException x) {
                    LOGGER.log(Level.WARNING, "found unexpected dir {0}", name);
                    continue;
                }
                File buildXml = new File(kid, "build.xml");
                if (!buildXml.isFile()) {
                    LOGGER.log(Level.WARNING, "found no build.xml in {0}", name);
                    continue;
                }
                String xml = FileUtils.readFileToString(buildXml, Charsets.UTF_8);
                Matcher m = NUMBER_ELT.matcher(xml);
                if (!m.find()) {
                    LOGGER.log(Level.WARNING, "could not find <number> in {0}/build.xml", name);
                    continue;
                }
                int number = Integer.parseInt(m.group(1));
                String nl = m.group(2);
                xml = m.replaceFirst("  <id>" + name + "</id>" + nl + "  <timestamp>" + timestamp + "</timestamp>" + nl);
                File newKid = new File(dir, Integer.toString(number));
                if (!kid.renameTo(newKid)) {
                    LOGGER.log(Level.WARNING, "failed to rename {0} to {1}", new Object[] {name, number});
                    continue;
                }
                FileUtils.writeStringToFile(new File(newKid, "build.xml"), xml, Charsets.UTF_8);
                LOGGER.log(Level.FINE, "fully processed {0} → {1}", new Object[] {name, number});
                idToNumber.put(name, number);
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, "failed to process " + kid, x);
            }
        }
    }

    /**
     * Look up a historical run by ID.
     * @param id a nonnumeric ID which may be a valid {@link Run#getId}
     * @return the corresponding {@link Run#number}, or 0 if unknown
     */
    public synchronized int findNumber(@Nonnull String id) {
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
