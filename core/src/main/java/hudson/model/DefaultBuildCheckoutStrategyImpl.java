package hudson.model;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run.RunnerAbortedException;
import hudson.model.listeners.SCMListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.NullChangeLogParser;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

public class DefaultBuildCheckoutStrategyImpl extends BuildCheckoutStrategy {
	
	@DataBoundConstructor
	public DefaultBuildCheckoutStrategyImpl() {		
	}

	@Override
	protected void preCheckout(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
			throws IOException, InterruptedException {		
		if (build.getProject() instanceof BuildableItemWithBuildWrappers) {
            BuildableItemWithBuildWrappers biwbw = (BuildableItemWithBuildWrappers) build.getProject();
            for (BuildWrapper bw : biwbw.getBuildWrappersList())
                bw.preCheckout(build,launcher,listener);
        }
		
	}

	@Override
	protected void checkout(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws Exception {
		for (int retryCount=build.getProject().getScmCheckoutRetryCount(); ; retryCount--) {
            // for historical reasons, null in the scm field means CVS, so we need to explicitly set this to something
            // in case check out fails and leaves a broken changelog.xml behind.
            // see http://www.nabble.com/CVSChangeLogSet.parse-yields-SAXParseExceptions-when-parsing-bad-*AccuRev*-changelog.xml-files-td22213663.html
            build.setScm(NullChangeLogParser.INSTANCE);

            try {
                if (build.getProject().checkout(build,launcher,listener,new File(build.getRootDir(),"changelog.xml"))) {
                    // check out succeeded
                    SCM scm = build.getProject().getScm();

                    build.setScm(scm.createChangeLogParser());
                    build.setChangeSet(new WeakReference<ChangeLogSet<? extends Entry>>(build.calcChangeSet()));

                    for (SCMListener l : Jenkins.getInstance().getSCMListeners())
                        l.onChangeLogParsed(build,listener,build.getChangeSet());
                    return;
                }
            } catch (AbortException e) {
                listener.error(e.getMessage());
            } catch (InterruptedIOException e) {
                throw (InterruptedException)new InterruptedException().initCause(e);
            } catch (IOException e) {
                // checkout error not yet reported
                e.printStackTrace(listener.getLogger());
            }

            if (retryCount == 0)   // all attempts failed
                throw new RunnerAbortedException();

            listener.getLogger().println("Retrying after 10 seconds");
            Thread.sleep(10000);
        }		
	}
	
	@Extension
    public static class DescriptorImpl extends BuildCheckoutStrategyDescriptor {
        @Override
        public String getDisplayName() {
            return "Classic";
        }
    }
}