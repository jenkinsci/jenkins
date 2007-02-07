package hudson.tasks;

import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.User;
import hudson.model.UserPropertyDescriptor;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.kohsuke.stapler.StaplerRequest;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Publisher} that sends the build result in e-mail.
 *
 * @author Kohsuke Kawaguchi
 */
public class Mailer extends Publisher {
    private static final Logger LOGGER = Logger.getLogger(Mailer.class.getName());

    /**
     * Whitespace-separated list of e-mail addresses that represent recipients.
     */
    public String recipients;

    /**
     * If true, only the first unstable build will be reported.
     */
    public boolean dontNotifyEveryUnstableBuild;

    /**
     * If true, individuals will receive e-mails regarding who broke the build.
     */
    public boolean sendToIndividuals;

    // TODO: left so that XStream won't get angry. figure out how to set the error handling behavior
    // in XStream.
    private transient String from;
    private transient String subject;
    private transient boolean failureOnly;

    public boolean perform(Build build, Launcher launcher, BuildListener listener) throws InterruptedException {
        if(debug)
            listener.getLogger().println("Running mailer");
        return new MailSender<Project,Build>(recipients,dontNotifyEveryUnstableBuild,sendToIndividuals) {
            /** Check whether a path (/-separated) will be archived. */
            @Override
            public boolean artifactMatches(String path, Build build) {
                ArtifactArchiver aa = (ArtifactArchiver) build.getProject().getPublishers().get(ArtifactArchiver.DESCRIPTOR);
                if (aa == null) {
                    LOGGER.finer("No ArtifactArchiver found");
                    return false;
                }
                String artifacts = aa.getArtifacts();
                for (String include : artifacts.split("[, ]+")) {
                    String pattern = include.replace(File.separatorChar, '/');
                    if (pattern.endsWith("/")) {
                        pattern += "**";
                    }
                    if (SelectorUtils.matchPath(pattern, path)) {
                        LOGGER.log(Level.FINER, "DescriptorImpl.artifactMatches true for {0} against {1}", new Object[] {path, pattern});
                        return true;
                    }
                }
                LOGGER.log(Level.FINER, "DescriptorImpl.artifactMatches for {0} matched none of {1}", new Object[] {path, artifacts});
                return false;
            }
        }.execute(build,listener);
    }

    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Publisher> {
        /**
         * The default e-mail address suffix appended to the user name found from changelog,
         * to send e-mails. Null if not configured.
         */
        private String defaultSuffix;

        /**
         * Hudson's own URL, to put into the e-mail.
         */
        private String hudsonUrl;

        /**
         * If non-null, use SMTP-AUTH with these information.
         */
        private String smtpAuthPassword,smtpAuthUsername;

        /**
         * The e-mail address that Hudson puts to "From:" field in outgoing e-mails.
         * Null if not configured.
         */
        private String adminAddress;

        /**
         * The SMTP server to use for sending e-mail. Null for default to the environment,
         * which is usually <tt>localhost</tt>.
         */
        private String smtpHost;

        public DescriptorImpl() {
            super(Mailer.class);
            load();
        }

        /**
         * For backward compatibility.
         */
        protected void convert(Map<String, Object> oldPropertyBag) {
            defaultSuffix = (String)oldPropertyBag.get("mail.default.suffix");
            hudsonUrl = (String)oldPropertyBag.get("mail.hudson.url");
            smtpAuthUsername = (String)oldPropertyBag.get("mail.hudson.smtpauth.username");
            smtpAuthPassword = (String)oldPropertyBag.get("mail.hudson.smtpauth.password");
            adminAddress = (String)oldPropertyBag.get("mail.admin.address");
            smtpHost = (String)oldPropertyBag.get("mail.smtp.host");
        }

        public String getDisplayName() {
            return "E-mail Notification";
        }

        public String getHelpFile() {
            return "/help/project-config/mailer.html";
        }

        public String getDefaultSuffix() {
            return defaultSuffix;
        }

        /** JavaMail session. */
        public Session createSession() {
            Properties props = new Properties(System.getProperties());
            if(smtpHost!=null)
                props.put("mail.smtp.host",smtpHost);

            return Session.getInstance(props,getAuthenticator());
        }

        private Authenticator getAuthenticator() {
            final String un = getSmtpAuthUserName();
            if(un==null)    return null;
            return new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(getSmtpAuthUserName(),getSmtpAuthPassword());
                }
            };
        }

        public boolean configure(StaplerRequest req) throws FormException {
            // this code is brain dead
            smtpHost = nullify(req.getParameter("mailer_smtp_server"));
            adminAddress = req.getParameter("mailer_admin_address");
            defaultSuffix = nullify(req.getParameter("mailer_default_suffix"));
            String url = nullify(req.getParameter("mailer_hudson_url"));
            if(url!=null && !url.endsWith("/"))
                url += '/';
            hudsonUrl = url;

            if(req.getParameter("mailer.useSMTPAuth")!=null) {
                smtpAuthUsername = nullify(req.getParameter("mailer.SMTPAuth.userName"));
                smtpAuthPassword = nullify(req.getParameter("mailer.SMTPAuth.password"));
            } else {
                smtpAuthUsername = smtpAuthPassword = null;
            }

            save();
            return super.configure(req);
        }

        private String nullify(String v) {
            if(v!=null && v.length()==0)    v=null;
            return v;
        }

        public String getSmtpServer() {
            return smtpHost;
        }

        public String getAdminAddress() {
            String v = adminAddress;
            if(v==null)     v = "address not configured yet <nobody>";
            return v;
        }

        public String getUrl() {
            return hudsonUrl;
        }

        public String getSmtpAuthUserName() {
            return smtpAuthUsername;
        }

        public String getSmtpAuthPassword() {
            return smtpAuthPassword;
        }

        public Publisher newInstance(StaplerRequest req) {
            Mailer m = new Mailer();
            req.bindParameters(m,"mailer_");
            return m;
        }
    }

    /**
     * Per user property that is e-mail address.
     */
    public static class UserProperty extends hudson.model.UserProperty {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        /**
         * The user's e-mail address.
         * Null to leave it to default.
         */
        private final String emailAddress;

        public UserProperty(String emailAddress) {
            this.emailAddress = emailAddress;
        }

        public String getAddress() {
            if(emailAddress!=null)
                return emailAddress;

            String ds = Mailer.DESCRIPTOR.getDefaultSuffix();
            if(ds!=null)
                return user.getId()+ds;
            else
                return null;
        }

        public DescriptorImpl getDescriptor() {
            return DESCRIPTOR;
        }

        public static final class DescriptorImpl extends UserPropertyDescriptor {
            public DescriptorImpl() {
                super(UserProperty.class);
            }

            public String getDisplayName() {
                return "E-mail";
            }

            public UserProperty newInstance(User user) {
                return new UserProperty(null);
            }

            public UserProperty newInstance(StaplerRequest req) throws FormException {
                return new UserProperty(req.getParameter("email.address"));
            }
        }
    }

    /**
     * Debug probe point to be activated by the scripting console.
     */
    public static boolean debug = false;
}
