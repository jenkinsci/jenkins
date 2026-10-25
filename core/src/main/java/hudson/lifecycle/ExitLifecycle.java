/*
 * The MIT License
 *
 * Copyright 2018 Alon Bar-Lev <alon.barlev@gmail.com>
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

package hudson.lifecycle;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.BootFailure;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link Lifecycle} that delegates the responsibility to restart Jenkins to an external
 * watchdog such as SystemD or OpenRC.
 *
 * <p>
 * Restart by exit with specific code.
 *
 * @author Alon Bar-Lev
 */
@Restricted(NoExternalUse.class)
public class ExitLifecycle extends Lifecycle {

    private static final Logger LOGGER = Logger.getLogger(ExitLifecycle.class.getName());

    private static final String EXIT_CODE_ON_RESTART = "exitCodeOnRestart";
    private static final String DEFAULT_EXIT_CODE = "5";

    private Integer exitOnRestart;

    public ExitLifecycle() {
        exitOnRestart = Integer.parseInt(SystemProperties.getString(Jenkins.class.getName() + "." + EXIT_CODE_ON_RESTART, DEFAULT_EXIT_CODE));
    }

    @Override
    @SuppressFBWarnings(value = "DM_EXIT", justification = "Exit is really intended.")
    public void restart() {
        Jenkins jenkins = Jenkins.getInstanceOrNull(); // guard against repeated concurrent calls to restart

        try {
            if (jenkins != null) {
                jenkins.cleanUp();
            }
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to clean up. Restart will continue.", e);
        }

        System.exit(exitOnRestart);
    }

    @Override
    public void onBootFailure(BootFailure problem) {
        restart();
    }
}
