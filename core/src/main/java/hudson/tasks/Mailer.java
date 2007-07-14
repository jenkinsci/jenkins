package hudson.tasks;

import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.User;
import hudson.model.UserPropertyDescriptor;
import hudson.util.FormFieldValidator;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.servlet.ServletException;

import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * {@link Publisher} that sends the build result in e-mail.
 *
 * @author Kohsuke Kawaguchi
 */
public class Mailer extends Publisher {
    private static final Logger LOGGER = Logger.getLogger(Mailer.class.getName());

    /**
     * Matches strings like "Kohsuke Kawaguchi &lt;kohsuke.kawaguchi@sun.com>"
     * @see #extractAddressFromId(String)
     */
    public static final String EMAIL_ADDRESS_REGEXP = "^.*<([^>]+)>.*$";

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
        return _perform(build,launcher,listener);
    }

    public <P extends Project<P,B>,B extends Build<P,B>> boolean _perform(B build, Launcher launcher, BuildListener listener) throws InterruptedException {
        if(debug)
            listener.getLogger().println("Running mailer");
        return new MailSender<P,B>(recipients,dontNotifyEveryUnstableBuild,sendToIndividuals) {
            /** Check whether a path (/-separated) will be archived. */
            @Override
            public boolean artifactMatches(String path, B build) {
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
        
        /**
         * If true use SSL on port 465 (standard SMTPS).
         */
        private boolean useSsl;
        

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
            if (useSsl) {
            	/* This allows the user to override settings by setting system properties but
            	 * also allows us to use the default SMTPs port of 465 if no port is already set.
            	 * It would be cleaner to use smtps, but that's done by calling session.getTransport()...
            	 * and thats done in mail sender, and it would be a bit of a hack to get it all to
            	 * coordinate, and we can make it work through setting mail.smtp properties.
            	 */
            	props.put("mail.smtp.auth","true");
            	if (props.getProperty("mail.smtp.socketFactory.port") == null) {
				    props.put("mail.smtp.port", "465");
    				props.put("mail.smtp.socketFactory.port", "465");
            	}
            	if (props.getProperty("mail.smtp.socketFactory.class") == null) {
            		props.put("mail.smtp.socketFactory.class","javax.net.ssl.SSLSocketFactory");
            	}
				props.put("mail.smtp.socketFactory.fallback", "false");
			}
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
            useSsl = req.getParameter("mailer_smtp_use_ssl")!=null;
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
        
        public boolean getUseSsl() {
        	return useSsl;
        }

        public Publisher newInstance(StaplerRequest req) {
            Mailer m = new Mailer();
            req.bindParameters(m,"mailer_");
            return m;
        }

        public void doAddressCheck(StaplerRequest req, StaplerResponse rsp,
                                   @QueryParameter("value") final String value) throws IOException, ServletException {
            new FormFieldValidator(req,rsp,false) {
                protected void check() throws IOException, ServletException {
                    try {
                        new InternetAddress(value);
                        ok();
                    } catch (AddressException e) {
                        error(e.getMessage());
                    }
                }
            }.process();
        }
    }

    /**
     * Tries to extract an email address from the user id, or returns null
     */
    public static String extractAddressFromId(String id) {
    	if (id.matches(EMAIL_ADDRESS_REGEXP))
    		return id.replaceFirst(EMAIL_ADDRESS_REGEXP, "$1");
    	return null;
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

        	String extractedAddress = extractAddressFromId(user.getId());
        	if (extractedAddress != null)
        		return extractedAddress;

            if(user.getId().contains("@"))
                // this already looks like an e-mail ID
                return user.getId();
            
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
