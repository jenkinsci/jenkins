package hudson.maven.reporters;

import hudson.Launcher;
import hudson.maven.MavenBuild;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MavenModule;
import hudson.model.BuildListener;
import hudson.tasks.MailSender;
import hudson.tasks.Mailer;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

import net.sf.json.JSONObject;

/**
 * Sends out an e-mail notification for Maven build result.
 * @author Kohsuke Kawaguchi
 */
public class MavenMailer extends MavenReporter {
    /**
     * @see Mailer
     */
    public String recipients;
    public boolean dontNotifyEveryUnstableBuild;
    public boolean sendToIndividuals;

    public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        new MailSender(recipients,dontNotifyEveryUnstableBuild,sendToIndividuals).execute(build,listener);
        return true;
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        public String getDisplayName() {
            return Messages.MavenMailer_DisplayName();
        }

        public String getHelpFile() {
            return "/help/project-config/mailer.html";
        }

        // reuse the config from the mailer.
        @Override
        public String getConfigPage() {
            return getViewPage(Mailer.class,"config.jelly");
        }

        public MavenReporter newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            MavenMailer m = new MavenMailer();
            req.bindParameters(m,"mailer_");
            m.dontNotifyEveryUnstableBuild = req.getParameter("mailer_notifyEveryUnstableBuild")==null;
            return m;
        }
    }

    private static final long serialVersionUID = 1L;
}
