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
        new MailSender<MavenModule,MavenBuild>(recipients,dontNotifyEveryUnstableBuild,sendToIndividuals).execute(build,listener);
        return true;
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private DescriptorImpl() {
            super(MavenMailer.class);
        }

        public String getDisplayName() {
            return "E-mail Notification";
        }

        public String getHelpFile() {
            return "/help/project-config/mailer.html";
        }

        // reuse the config from the mailer.
        @Override
        public String getConfigPage() {
            return getViewPage(Mailer.class,"config.jelly");
        }

        public MavenMailer newInstance(StaplerRequest req) {
            MavenMailer m = new MavenMailer();
            req.bindParameters(m,"mailer_");
            return m;
        }
    }

    private static final long serialVersionUID = 1L;
}
