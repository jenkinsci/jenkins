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
package jenkins;

import hudson.Extension;
import hudson.Main;
import hudson.model.AsyncPeriodicWork;
import hudson.model.DownloadService;
import hudson.model.TaskListener;
import hudson.model.UpdateSite;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements a daily update check for update sites and {@link hudson.model.DownloadService.Downloadable}s that are due.
 *
 * Note that this does not mean that update center information is at most 24 hours old, but rather 24-48 hours.
 * Downloadables are by default due every 24 hours, as are update sites. This check runs every 24 hours, but only updates
 * what is already due, i.e. older than 24 hours. So this ensures that update site information is never older than 48 hours.
 */
@Extension
@Restricted(NoExternalUse.class)
@Symbol("updateCenterCheck")
// Moved from DownloadSettings.DailyCheck when the option to download in the browser was removed
public final class DailyCheck extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(DailyCheck.class.getName());

    public DailyCheck() {
        super("Download metadata");
    }

    @Override public long getRecurrencePeriod() {
        return DAY;
    }

    @Override public long getInitialDelay() {
        return Main.isUnitTest ? DAY : 0;
    }

    @Override protected void execute(TaskListener listener) throws IOException, InterruptedException {
        boolean due = false;
        for (UpdateSite site : Jenkins.get().getUpdateCenter().getSites()) {
            if (site.isDue()) {
                due = true;
                break;
            }
        }
        if (!due) {
            // JENKINS-32886: downloadables like the tool installer data may have never been tried if the plugin
            // was installed "after a restart", so let's give them a try here.
            final long now = System.currentTimeMillis();
            for (DownloadService.Downloadable d : DownloadService.Downloadable.all()) {
                if (d.getDue() <= now) {
                    try {
                        d.updateNow();
                    } catch(Exception e) {
                        LOGGER.log(Level.WARNING, String.format("Unable to update downloadable [%s]", d.getId()), e);
                    }
                }
            }
            return;
        }
        // This checks updates of the update sites and downloadables.
        HttpResponse rsp = Jenkins.get().getPluginManager().doCheckUpdatesServer();
        if (rsp instanceof FormValidation) {
            listener.error(((FormValidation) rsp).renderHtml());
        }
    }

}