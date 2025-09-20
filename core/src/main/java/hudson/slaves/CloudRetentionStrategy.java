/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

package hudson.slaves;


import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.logging.Level.WARNING;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import net.jcip.annotations.GuardedBy;

/**
 * {@link RetentionStrategy} implementation for {@link AbstractCloudComputer} that terminates
 * it if it remains idle for X minutes.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.382
 */
public class CloudRetentionStrategy extends RetentionStrategy<AbstractCloudComputer> {
    private int idleMinutes;

    public CloudRetentionStrategy(int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    @Override
    @GuardedBy("hudson.model.Queue.lock")
    public long check(final AbstractCloudComputer c) {
        final AbstractCloudSlave computerNode = c.getNode();
        if (c.isIdle() && !disabled && computerNode != null) {
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMilliseconds > MINUTES.toMillis(idleMinutes)) {
                LOGGER.log(Level.INFO, "Disconnecting {0}", c.getName());
                try {
                    computerNode.terminate();
                } catch (InterruptedException | IOException e) {
                    LOGGER.log(WARNING, "Failed to terminate " + c.getName(), e);
                }
            }
        }
        return 0;
    }

    /**
     * Try to connect to it ASAP.
     */
    @Override
    public void start(AbstractCloudComputer c) {
        c.connect(false);
    }

    private static final Logger LOGGER = Logger.getLogger(CloudRetentionStrategy.class.getName());

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static boolean disabled = SystemProperties.getBoolean(CloudRetentionStrategy.class.getName() + ".disabled");
}
