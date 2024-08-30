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

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Job;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Background task actually running background build discarders.
 *
 * @see GlobalBuildDiscarderConfiguration
 * @see GlobalBuildDiscarderStrategy
 */
@Restricted(NoExternalUse.class)
@Extension
public class BackgroundGlobalBuildDiscarder extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(BackgroundGlobalBuildDiscarder.class.getName());

    public BackgroundGlobalBuildDiscarder() {
        super("Periodic background build discarder"); // TODO i18n
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        for (Job job : Jenkins.get().allItems(Job.class)) {
            processJob(listener, job);
        }
    }

    public static void processJob(TaskListener listener, Job job) {
        GlobalBuildDiscarderConfiguration.get().getConfiguredBuildDiscarders().forEach(strategy -> {
            String displayName = strategy.getDescriptor().getDisplayName();
            if (strategy.isApplicable(job)) {
                try {
                    strategy.apply(job);
                } catch (Exception ex) {
                    listener.error("An exception occurred when executing " + displayName + ": " + ex.getMessage());
                    LOGGER.log(Level.WARNING, "An exception occurred when executing " + displayName, ex);
                }
            }
        });
    }

    @Override
    public long getRecurrencePeriod() {
        return HOUR;
    }
}
