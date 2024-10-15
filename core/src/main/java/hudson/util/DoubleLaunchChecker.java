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

package hudson.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Main;
import hudson.Util;
import hudson.model.AdministrativeMonitor;
import hudson.model.AperiodicWork;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Makes sure that no other Hudson uses our {@code JENKINS_HOME} directory,
 * to forestall the problem of running multiple instances of Hudson that point to the same data directory.
 *
 * <p>
 * This set up error occasionally happens especially when the user is trying to reassign the context path of the app,
 * and it results in a hard-to-diagnose error, so we actively check this.
 *
 * <p>
 * The mechanism is simple. This class occasionally updates a known file inside the hudson home directory,
 * and whenever it does so, it monitors the timestamp of the file to make sure no one else is updating
 * this file. In this way, while we cannot detect the problem right away, within a reasonable time frame
 * we can detect the collision.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.178
 */
@Extension
public class DoubleLaunchChecker extends AdministrativeMonitor {
    /**
     * The timestamp of the owner file when we updated it for the last time.
     * 0 to indicate that there was no update before.
     */
    private long lastWriteTime = 0L;

    private boolean activated;

    public final File home;

    /**
     * ID string of the other Hudson that we are colliding with.
     * Can be null.
     */
    private String collidingId;

    public DoubleLaunchChecker() {
        home = Jenkins.get().getRootDir();
    }

    @Override
    public String getDisplayName() {
        return Messages.DoubleLaunchChecker_duplicate_jenkins_checker();
    }

    @Override
    public boolean isActivated() {
        return activated;
    }

    protected void execute() {
        LOGGER.fine("running detector");
        File timestampFile = new File(home, ".owner");

        long t = timestampFile.lastModified();
        if (t != 0 && lastWriteTime != 0 && t != lastWriteTime && isEnabled()) {
            try {
                collidingId = Files.readString(Util.fileToPath(timestampFile), Charset.defaultCharset());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to read collision file", e);
            }
            // we noticed that someone else have updated this file.
            activated = true;
            LOGGER.severe("Collision detected. timestamp=" + t + ", expected=" + lastWriteTime);
            // we need to continue updating this file, so that the other Hudson would notice the problem, too.
        }

        try {
            Files.writeString(Util.fileToPath(timestampFile), getId(), Charset.defaultCharset());
            lastWriteTime = timestampFile.lastModified();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, null, e);
            // if failed to write, err on the safe side and assume things are OK.
            lastWriteTime = 0;
        }
    }

    /**
     * Figures out a string that identifies this instance of Hudson.
     */
    public String getId() {
        return Long.toString(ProcessHandle.current().pid());
    }

    public String getCollidingId() {
        return collidingId;
    }

    @SuppressFBWarnings(value = "PREDICTABLE_RANDOM", justification = "The random is just used for load distribution.")
    @Extension
    public static final class Schedule extends AperiodicWork {

        private final Random random = new Random();

        @Override
        public AperiodicWork getNewInstance() {
            // Awkward to use DoubleLaunchChecker itself as the AperiodicWork since it is stateful, and we may not return this.
            return new Schedule();
        }

        @Override
        public long getRecurrencePeriod() {
            // randomize the scheduling so that multiple Jenkins instances will write at the file at different time
            return (Main.isUnitTest ? Duration.ofSeconds(random.nextInt(10) + 20) : Duration.ofMinutes(random.nextInt(30) + 60)).toMillis();
        }

        @Override
        protected void doAperiodicRun() {
            ExtensionList.lookupSingleton(DoubleLaunchChecker.class).execute();
        }

    }

    private static final Logger LOGGER = Logger.getLogger(DoubleLaunchChecker.class.getName());
}
