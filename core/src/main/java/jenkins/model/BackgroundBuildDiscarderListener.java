package jenkins.model;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.listeners.RunListener;
import hudson.util.LogTaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Run background build discarders on an individual job once a build is finalized
 */
@Extension
@Restricted(NoExternalUse.class)
public class BackgroundBuildDiscarderListener extends RunListener<Run> {

    private static final Logger LOGGER = Logger.getLogger(BackgroundBuildDiscarderListener.class.getName());

    @Override
    public void onFinalized(Run run) {
        Job job = run.getParent();
        BackgroundBuildDiscarder.processJob(new LogTaskListener(LOGGER, Level.FINE), job);
    }
}
