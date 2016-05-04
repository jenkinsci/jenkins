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
import hudson.Main;
import hudson.model.AdministrativeMonitor;
import hudson.model.AsyncPeriodicWork;
import hudson.model.DownloadService;
import hudson.model.TaskListener;
import hudson.model.UpdateSite;
import hudson.util.FormValidation;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Lets user configure how metadata files should be downloaded.
 * @see UpdateSite
 * @see DownloadService
 */
@Restricted(NoExternalUse.class) // no clear reason for this to be an API
@Extension @Symbol("downloadSettings")
public final class DownloadSettings extends GlobalConfiguration {

    public static DownloadSettings get() {
        return Jenkins.getInstance().getInjector().getInstance(DownloadSettings.class);
    }

    private boolean useBrowser = false;
    
    public DownloadSettings() {
        load();
    }

    @Override public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        return true;
    }

    public boolean isUseBrowser() {
        return useBrowser;
    }

    public void setUseBrowser(boolean useBrowser) {
        this.useBrowser = useBrowser;
        save();
    }

    @Override public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public static boolean usePostBack() {
        return get().isUseBrowser() && Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER);
    }

    public static void checkPostBackAccess() throws AccessDeniedException {
        if (!get().isUseBrowser()) {
            throw new AccessDeniedException("browser-based download disabled");
        }
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
    }

    @Extension @Symbol("updateCenterCheck")
    public static final class DailyCheck extends AsyncPeriodicWork {

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
            if (get().isUseBrowser()) {
                return;
            }
            boolean due = false;
            for (UpdateSite site : Jenkins.getInstance().getUpdateCenter().getSites()) {
                if (site.isDue()) {
                    due = true;
                    break;
                }
            }
            if (!due) {
                return;
            }
            HttpResponse rsp = Jenkins.getInstance().getPluginManager().doCheckUpdatesServer();
            if (rsp instanceof FormValidation) {
                listener.error(((FormValidation) rsp).renderHtml());
            }
        }

    }

    @Extension public static final class Warning extends AdministrativeMonitor {

        @Override public boolean isActivated() {
            return DownloadSettings.get().isUseBrowser();
        }

    }

}
