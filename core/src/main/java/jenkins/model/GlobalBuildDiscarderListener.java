/*
 * The MIT License
 *
 * Copyright 2019 Daniel Beck
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

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.listeners.RunListener;
import hudson.remoting.SingleLaneExecutorService;
import hudson.util.LogTaskListener;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.Timer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Run build discarders on an individual job once a build is finalized
 */
@Extension
@Restricted(NoExternalUse.class)
public class GlobalBuildDiscarderListener extends RunListener<Run> {

    private static final Logger LOGGER = Logger.getLogger(GlobalBuildDiscarderListener.class.getName());

    private final ExecutorService executor = new SingleLaneExecutorService(Timer.get());

    @Override
    public void onFinalized(Run run) {
        executor.execute(() -> {
            Job job = run.getParent();
            try {
                // Job-level build discarder execution is unconditional.
                job.logRotate();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, e, () -> "Failed to rotate log for " + run);
            }
            // Avoid calling Job.logRotate twice in case JobGlobalBuildDiscarderStrategy is configured globally.
            BackgroundGlobalBuildDiscarder.processJob(new LogTaskListener(LOGGER, Level.FINE), job,
                    GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().stream()
                            .filter(s -> !(s instanceof JobGlobalBuildDiscarderStrategy)));
        });
    }

    /**
     * Waits for all currently scheduled or running discards to complete.
     */
    @VisibleForTesting
    @Restricted(DoNotUse.class)
    public static void await() throws Exception {
        ExtensionList.lookupSingleton(GlobalBuildDiscarderListener.class).executor.submit(() -> {}).get();
    }

}
