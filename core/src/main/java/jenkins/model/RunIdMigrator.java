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

import hudson.Extension;
import hudson.Util;
import hudson.model.Job;
import hudson.model.RootAction;
import hudson.model.Run;
import hudson.util.AtomicFileWriter;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.tools.ant.BuildException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

import static java.util.logging.Level.*;

/**
 * Converts legacy {@code builds} directories to the current format.
 *
 * There would be one instance associated with each {@link Job}, to retain ID -> build# mapping.
 *
 * The {@link Job#getBuildDir} is passed to every method call (rather than being cached) in case it is moved.
 */
@Restricted(NoExternalUse.class)
public final class RunIdMigrator {

    private final DateFormat legacyIdFormatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    static final Logger LOGGER = Logger.getLogger(RunIdMigrator.class.getName());
    private static final String MAP_FILE = "legacyIds";
    /** avoids wasting a map for new jobs */
    private static final Map<String,Integer> EMPTY = new TreeMap<String,Integer>();

    /**
     * Did we record "unmigrate" instruction for this $JENKINS_HOME? Yes if it's in the set.
     */
    private static final Set<File> offeredToUnmigrate = Collections.synchronizedSet(new HashSet<File>());

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
            LOGGER.log(WARNING, "could not read from " + f, x);
        }
        return true;
    }

    private void save(File dir) {
        File f = new File(dir, MAP_FILE);
        try {
            AtomicFileWriter w = new AtomicFileWriter(f);
            try {
                for (Map.Entry<String,Integer> entry : idToNumber.entrySet()) {
                    w.write(entry.getKey() + ' ' + entry.getValue() + '\n');
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
        LOGGER.log(INFO, "Migrating build records in {0}", dir);
        doMigrate(dir);
        save(dir);
        if (jenkinsHome != null && offeredToUnmigrate.add(jenkinsHome))
            LOGGER.log(WARNING, "Build record migration (https://wiki.jenkins-ci.org/display/JENKINS/JENKINS-24380+Migration) is one-way. If you need to downgrade Jenkins, run: {0}", getUnmigrationCommandLine(jenkinsHome));
        return true;
    }

    private static String getUnmigrationCommandLine(File jenkinsHome) {
        StringBuilder cp = new StringBuilder();
        for (Class<?> c : new Class<?>[] {RunIdMigrator.class, /* TODO how to calculate transitive dependencies automatically? */Charsets.class, WriterOutputStream.class, BuildException.class, FastDateFormat.class}) {
            URL location = c.getProtectionDomain().getCodeSource().getLocation();
            String locationS = location.toString();
            if (location.getProtocol().equals("file")) {
                try {
                    locationS = new File(location.toURI()).getAbsolutePath();
                } catch (URISyntaxException x) {
                    // never mind
                }
            }
            if (cp.length() > 0) {
                cp.append(File.pathSeparator);
            }
            cp.append(locationS);
        }
        return String.format("java -classpath \"%s\" %s \"%s\"", cp, RunIdMigrator.class.getName(), jenkinsHome);
    }

    private static final Pattern NUMBER_ELT = Pattern.compile("(?m)^  <number>(\\d+)</number>(\r?\n)");
    private void doMigrate(File dir) {
        idToNumber = new TreeMap<String,Integer>();
        File[] kids = dir.listFiles();
        // Need to process symlinks first so we can rename to them.
        List<File> kidsList = new ArrayList<File>(Arrays.asList(kids));
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
                String xml = FileUtils.readFileToString(buildXml, Charsets.UTF_8);
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
                FileUtils.writeStringToFile(new File(newKid, "build.xml"), xml, Charsets.UTF_8);
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
        Class<?> pathC;
        try {
            pathC = Class.forName("java.nio.file.Path");
        } catch (ClassNotFoundException x) {
            // Java 6, do our best
            if (dest.exists()) {
                throw new IOException(dest + " already exists");
            }
            if (src.renameTo(dest)) {
                return;
            }
            throw new IOException("could not move " + src + " to " + dest);
        }
        try {
            Method toPath = File.class.getMethod("toPath");
            Class<?> copyOptionAC = Class.forName("[Ljava.nio.file.CopyOption;");
            Class.forName("java.nio.file.Files").getMethod("move", pathC, pathC, copyOptionAC).invoke(null, toPath.invoke(src), toPath.invoke(dest), Array.newInstance(copyOptionAC.getComponentType(), 0));
        } catch (InvocationTargetException x) {
            Throwable cause = x.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new IOException(cause);
            }
        } catch (Exception x) {
            throw new IOException(x);
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

    /**
     * Reverses the migration, in case you want to revert to the older format.
     * @param args one parameter, {@code $JENKINS_HOME}
     */
    public static void main(String... args) throws Exception {
        if (args.length != 1) {
            throw new Exception("pass one parameter, $JENKINS_HOME");
        }
        File root = new File(args[0]);
        File jobs = new File(root, "jobs");
        if (!jobs.isDirectory()) {
            throw new FileNotFoundException("no such $JENKINS_HOME " + root);
        }
        new RunIdMigrator().unmigrateJobsDir(jobs);
    }
    private void unmigrateJobsDir(File jobs) throws Exception {
        File[] jobDirs = jobs.listFiles();
        if (jobDirs == null) {
            System.err.println(jobs + " claimed to exist, but cannot be listed");
            return;
        }
        for (File job : jobDirs) {

            if (job.getName().equals("builds")) {
                unmigrateBuildsDir(job);
            }

            File[] kids = job.listFiles();
            if (kids == null) {
                continue;
            }
            for (File kid : kids) {
                if (!kid.isDirectory()) {
                    continue;
                }
                if (kid.getName().equals("builds")) {
                    unmigrateBuildsDir(kid);
                } else {
                    // Might be jobs, modules, promotions, etc.; we assume an ItemGroup.getRootDirFor implementation returns grandchildren.
                    unmigrateJobsDir(kid);
                }
            }
        }
    }
    private static final Pattern ID_ELT = Pattern.compile("(?m)^  <id>([0-9_-]+)</id>(\r?\n)");
    private static final Pattern TIMESTAMP_ELT = Pattern.compile("(?m)^  <timestamp>(\\d+)</timestamp>(\r?\n)");
    /** Inverse of {@link #doMigrate}. */
    private void unmigrateBuildsDir(File builds) throws Exception {
        File mapFile = new File(builds, MAP_FILE);
        if (!mapFile.isFile()) {
            System.err.println(builds + " does not look to have been migrated yet; skipping");
            return;
        }
        for (File build : builds.listFiles()) {
            int number;
            try {
                number = Integer.parseInt(build.getName());
            } catch (NumberFormatException x) {
                continue;
            }
            File buildXml = new File(build, "build.xml");
            if (!buildXml.isFile()) {
                System.err.println(buildXml + " did not exist");
                continue;
            }
            String xml = FileUtils.readFileToString(buildXml, Charsets.UTF_8);
            Matcher m = TIMESTAMP_ELT.matcher(xml);
            if (!m.find()) {
                System.err.println(buildXml + " did not contain <timestamp> as expected");
                continue;
            }
            long timestamp = Long.parseLong(m.group(1));
            String nl = m.group(2);
            xml = m.replaceFirst("  <number>" + number + "</number>" + nl);
            m = ID_ELT.matcher(xml);
            String id;
            if (m.find()) {
                id = m.group(1);
                xml = m.replaceFirst("");
            } else {
                // Post-migration build. We give it a new ID based on its timestamp.
                id = legacyIdFormatter.format(new Date(timestamp));
            }
            FileUtils.write(buildXml, xml, Charsets.UTF_8);
            if (!build.renameTo(new File(builds, id))) {
                System.err.println(build + " could not be renamed");
            }
            Util.createSymlink(builds, id, Integer.toString(number), StreamTaskListener.fromStderr());
        }
        Util.deleteFile(mapFile);
        System.err.println(builds + " has been restored to its original format");
    }

    /**
     * Expose unmigration instruction to the user.
     */
    @Extension
    public static class UnmigrationInstruction implements RootAction, StaplerProxy {
        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "JENKINS-24380";
        }

        @Override
        public Object getTarget() {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            return this;
        }

        public String getCommand() {
            return RunIdMigrator.getUnmigrationCommandLine(Jenkins.getInstance().getRootDir());
        }
    }
}
