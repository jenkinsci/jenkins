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

@Extension
@Restricted(NoExternalUse.class)
@Symbol("updateCenterCheck")
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