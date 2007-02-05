package hudson.maven.reporters;

import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.tasks.Mailer;
import hudson.tasks.MailSender;
import hudson.model.BuildListener;
import org.kohsuke.stapler.StaplerRequest;
import org.apache.maven.project.MavenProject;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenMailer extends MavenReporter {
    /**
     * @see Mailer
     */
    public String recipients;
    public boolean dontNotifyEveryUnstableBuild;
    public boolean sendToIndividuals;


    public boolean postBuild(MavenBuildProxy build, MavenProject pom, final BuildListener listener) throws InterruptedException, IOException {
        build.execute(new BuildCallable<Void,IOException>() {
            public Void call(MavenBuild build) throws IOException, InterruptedException {
                new MailSender(recipients,dontNotifyEveryUnstableBuild,sendToIndividuals).execute(build,listener);
                return null;
            }
        });
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
}
