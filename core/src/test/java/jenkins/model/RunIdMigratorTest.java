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
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class RunIdMigratorTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** Ensures that legacy timestamps are interpreted in a predictable time zone. */
    @BeforeClass public static void timezone() {
        TimeZone.setDefault(TimeZone.getTimeZone("EST"));
    }

    @BeforeClass public static void logging() {
        RunIdMigrator.LOGGER.setLevel(Level.ALL);
        Handler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        RunIdMigrator.LOGGER.addHandler(handler);
    }

    private RunIdMigrator migrator;
    private File dir;

    @Before public void init() {
        migrator = new RunIdMigrator();
        dir = tmp.getRoot();
    }

    @Test public void newJob() throws Exception {
        migrator.created(dir);
        assertEquals("{legacyIds=''}", summarize());
        assertEquals(0, migrator.findNumber("whatever"));
        migrator.delete(dir, "1");
        migrator = new RunIdMigrator();
        assertFalse(migrator.migrate(dir, null));
        assertEquals("{legacyIds=''}", summarize());
    }

    @Test public void legacy() throws Exception {
        write("2014-01-02_03-04-05/build.xml", "<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <number>99</number>\n  <otherstuff>ok</otherstuff>\n</run>");
        link("99", "2014-01-02_03-04-05");
        link("lastFailedBuild", "-1");
        link("lastSuccessfulBuild", "99");
        assertEquals("{2014-01-02_03-04-05={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <number>99</number>\n  <otherstuff>ok</otherstuff>\n</run>'}, 99=→2014-01-02_03-04-05, lastFailedBuild=→-1, lastSuccessfulBuild=→99}", summarize());
        assertTrue(migrator.migrate(dir, null));
        assertEquals("{99={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <id>2014-01-02_03-04-05</id>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>'}, lastFailedBuild=→-1, lastSuccessfulBuild=→99, legacyIds='2014-01-02_03-04-05 99\n'}", summarize());
        assertEquals(99, migrator.findNumber("2014-01-02_03-04-05"));
        migrator = new RunIdMigrator();
        assertFalse(migrator.migrate(dir, null));
        assertEquals(99, migrator.findNumber("2014-01-02_03-04-05"));
        migrator.delete(dir, "2014-01-02_03-04-05");
        FileUtils.deleteDirectory(new File(dir, "99"));
        new File(dir, "lastSuccessfulBuild").delete();
        assertEquals("{lastFailedBuild=→-1, legacyIds=''}", summarize());
    }

    @Test public void reRunMigration() throws Exception {
        write("2014-01-02_03-04-04/build.xml", "<run>\n  <number>98</number>\n</run>");
        link("98", "2014-01-02_03-04-04");
        write("99/build.xml", "<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>");
        link("lastFailedBuild", "-1");
        link("lastSuccessfulBuild", "99");
        assertEquals("{2014-01-02_03-04-04={build.xml='<run>\n  <number>98</number>\n</run>'}, 98=→2014-01-02_03-04-04, 99={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>'}, lastFailedBuild=→-1, lastSuccessfulBuild=→99}", summarize());
        assertTrue(migrator.migrate(dir, null));
        assertEquals("{98={build.xml='<run>\n  <id>2014-01-02_03-04-04</id>\n  <timestamp>1388649844000</timestamp>\n</run>'}, 99={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>'}, lastFailedBuild=→-1, lastSuccessfulBuild=→99, legacyIds='2014-01-02_03-04-04 98\n'}", summarize());
    }

    @Test public void reverseImmediately() throws Exception {
        File root = dir;
        dir = new File(dir, "jobs/somefolder/jobs/someproject/promotions/OK/builds");
        write("99/build.xml", "<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <id>2014-01-02_03-04-05</id>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>");
        link("lastFailedBuild", "-1");
        link("lastSuccessfulBuild", "99");
        write("legacyIds", "2014-01-02_03-04-05 99\n");
        assertEquals("{99={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <id>2014-01-02_03-04-05</id>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>'}, lastFailedBuild=→-1, lastSuccessfulBuild=→99, legacyIds='2014-01-02_03-04-05 99\n'}", summarize());
        RunIdMigrator.main(root.getAbsolutePath());
        assertEquals("{2014-01-02_03-04-05={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <number>99</number>\n  <otherstuff>ok</otherstuff>\n</run>'}, 99=→2014-01-02_03-04-05, lastFailedBuild=→-1, lastSuccessfulBuild=→99}", summarize());
    }

    @Test public void reverseAfterNewBuilds() throws Exception {
        File root = dir;
        dir = new File(dir, "jobs/someproject/modules/test$test/builds");
        write("1/build.xml", "<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>");
        write("legacyIds", "");
        assertEquals("{1={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>'}, legacyIds=''}", summarize());
        RunIdMigrator.main(root.getAbsolutePath());
        assertEquals("{1=→2014-01-02_03-04-05, 2014-01-02_03-04-05={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <number>1</number>\n  <otherstuff>ok</otherstuff>\n</run>'}}", summarize());
    }

    @Test public void reverseMatrixAfterNewBuilds() throws Exception {
        File root = dir;
        dir = new File(dir, "jobs/someproject/Environment=prod/builds");
        write("1/build.xml", "<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>");
        write("legacyIds", "");
        assertEquals("{1={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>'}, legacyIds=''}", summarize());
        RunIdMigrator.main(root.getAbsolutePath());
        assertEquals("{1=→2014-01-02_03-04-05, 2014-01-02_03-04-05={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <number>1</number>\n  <otherstuff>ok</otherstuff>\n</run>'}}", summarize());
    }

    @Test public void reverseMavenAfterNewBuilds() throws Exception {
        File root = dir;
        dir = new File(dir, "jobs/someproject/test$test/builds");
        write("1/build.xml", "<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>");
        write("legacyIds", "");
        assertEquals("{1={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <timestamp>1388649845000</timestamp>\n  <otherstuff>ok</otherstuff>\n</run>'}, legacyIds=''}", summarize());
        RunIdMigrator.main(root.getAbsolutePath());
        assertEquals("{1=→2014-01-02_03-04-05, 2014-01-02_03-04-05={build.xml='<?xml version='1.0' encoding='UTF-8'?>\n<run>\n  <stuff>ok</stuff>\n  <number>1</number>\n  <otherstuff>ok</otherstuff>\n</run>'}}", summarize());
    }

    // TODO test sane recovery from various error conditions

    private void write(String file, String text) throws Exception {
        FileUtils.write(new File(dir, file), text);
    }

    private void link(String symlink, String dest) throws Exception {
        Util.createSymlink(dir, dest, symlink, new StreamTaskListener(System.out, Charset.defaultCharset()));
    }

    private String summarize() throws Exception {
        return summarize(dir);
    }
    private static String summarize(File dir) throws Exception {
        File[] kids = dir.listFiles();
        Map<String,String> m = new TreeMap<String,String>();
        for (File kid : kids) {
            String notation;
            String symlink = Util.resolveSymlink(kid);
            if (symlink != null) {
                notation = "→" + symlink;
            } else if (kid.isFile()) {
                notation = "'" + FileUtils.readFileToString(kid) + "'";
            } else if (kid.isDirectory()) {
                notation = summarize(kid);
            } else {
                notation = "?";
            }
            m.put(kid.getName(), notation);
        }
        return m.toString();
    }

    @Test public void move() throws Exception {
        File src = tmp.newFile();
        File dest = new File(tmp.getRoot(), "dest");
        RunIdMigrator.move(src, dest);
        File dest2 = tmp.newFile();
        try {
            RunIdMigrator.move(dest, dest2);
            fail();
        } catch (IOException x) {
            System.err.println("Got expected move exception: " + x);
        }
    }

}
