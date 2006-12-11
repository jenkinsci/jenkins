package hudson.tasks;

import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.User;
import hudson.model.UserPropertyDescriptor;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.kohsuke.stapler.StaplerRequest;

import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends the build result in e-mail.
 *
 * @author Kohsuke Kawaguchi
 */
public class Mailer extends Publisher {

    private static final Logger LOGGER = Logger.getLogger(Mailer.class.getName());

    private static final int MAX_LOG_LINES = 250;

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

    public boolean perform(Build build, Launcher launcher, BuildListener listener) {
        try {
            MimeMessage mail = getMail(build);
            if(mail!=null) {
                Address[] allRecipients = mail.getAllRecipients();
                if(allRecipients!=null) {
                StringBuffer buf = new StringBuffer("Sending e-mails to:");
                    for (Address a : allRecipients)
                    buf.append(' ').append(a);
                listener.getLogger().println(buf);
                Transport.send(mail);
                } else {
                    listener.getLogger().println("An attempt to send an e-mail"
                            + " to empty list of recipients, ignored.");
                }
            }
        } catch (MessagingException e) {
            e.printStackTrace( listener.error(e.getMessage()) );
        }

        return true;
    }

    private MimeMessage getMail(Build build) throws MessagingException {
        if(build.getResult()==Result.FAILURE) {
            return createFailureMail(build);
        }

        if(build.getResult()==Result.UNSTABLE) {
            Build prev = build.getPreviousBuild();
            if(!dontNotifyEveryUnstableBuild)
                return createUnstableMail(build);
            if(prev!=null) {
                if(prev.getResult()==Result.SUCCESS)
                    return createUnstableMail(build);
            }
        }

        if(build.getResult()==Result.SUCCESS) {
            Build prev = build.getPreviousBuild();
            if(prev!=null) {
                if(prev.getResult()==Result.FAILURE)
                    return createBackToNormalMail(build, "normal");
                if(prev.getResult()==Result.UNSTABLE)
                    return createBackToNormalMail(build, "stable");
            }
        }

        return null;
    }

    private MimeMessage createBackToNormalMail(Build build, String subject) throws MessagingException {
        MimeMessage msg = createEmptyMail(build);

        msg.setSubject(getSubject(build,"Hudson build is back to "+subject +": "));
        StringBuffer buf = new StringBuffer();
        appendBuildUrl(build,buf);
        msg.setText(buf.toString());

        return msg;
    }

    private MimeMessage createUnstableMail(Build build) throws MessagingException {
        MimeMessage msg = createEmptyMail(build);

        msg.setSubject(getSubject(build,"Hudson build became unstable: "));
        StringBuffer buf = new StringBuffer();
        appendBuildUrl(build,buf);
        msg.setText(buf.toString());

        return msg;
    }

    private void appendBuildUrl(Build build, StringBuffer buf) {
        String baseUrl = DESCRIPTOR.getUrl();
        if(baseUrl!=null) {
            buf.append("See ").append(baseUrl).append(Util.encode(build.getUrl())).append("\n\n");
        }
    }

    private MimeMessage createFailureMail(Build build) throws MessagingException {
        MimeMessage msg = createEmptyMail(build);

        msg.setSubject(getSubject(build, "Build failed in Hudson: "));

        StringBuffer buf = new StringBuffer();
        appendBuildUrl(build,buf);

        boolean firstChange = true;
        for (ChangeLogSet.Entry entry : build.getChangeSet()) {
            if (firstChange) {
                firstChange = false;
                buf.append("Changes:\n\n");
            }
            buf.append('[');
            buf.append(entry.getAuthor().getFullName());
            buf.append("] ");
            String m = entry.getMsg();
            buf.append(m);
            if (!m.endsWith("\n")) {
                buf.append('\n');
            }
            buf.append('\n');
        }

        buf.append("------------------------------------------\n");

        try {
            String log = build.getLog();
            String[] lines = log.split("\n");
            int start = 0;
            if (lines.length > MAX_LOG_LINES) {
                // Avoid sending enormous logs over email.
                // Interested users can always look at the log on the web server.
                buf.append("[...truncated " + (lines.length - MAX_LOG_LINES) + " lines...]\n");
                start = lines.length - MAX_LOG_LINES;
            }
            String workspaceUrl = null, artifactUrl = null;
            Pattern wsPattern = null;
            String baseUrl = DESCRIPTOR.getUrl();
            if (baseUrl != null) {
                // Hyperlink local file paths to the repository workspace or build artifacts.
                // Note that it is possible for a failure mail to refer to a file using a workspace
                // URL which has already been corrected in a subsequent build. To fix, archive.
                workspaceUrl = baseUrl + Util.encode(build.getProject().getUrl()) + "ws/";
                artifactUrl = baseUrl + Util.encode(build.getUrl()) + "artifact/";
                File workspaceDir = build.getProject().getWorkspace().getLocal();
                // Match either file or URL patterns, i.e. either
                // c:\hudson\workdir\jobs\foo\workspace\src\Foo.java
                // file:/c:/hudson/workdir/jobs/foo/workspace/src/Foo.java
                // will be mapped to one of:
                // http://host/hudson/job/foo/ws/src/Foo.java
                // http://host/hudson/job/foo/123/artifact/src/Foo.java
                // Careful with path separator between $1 and $2:
                // workspaceDir will not normally end with one;
                // workspaceDir.toURI() will end with '/' if and only if workspaceDir.exists() at time of call
                wsPattern = Pattern.compile("(" +
                    quoteRegexp(workspaceDir.getPath()) + "|" + quoteRegexp(workspaceDir.toURI().toString()) + ")[/\\\\]?([^:#\\s]*)");
            }
            for (int i = start; i < lines.length; i++) {
                String line = lines[i];
                if (wsPattern != null) {
                    // Perl: $line =~ s{$rx}{$path = $2; $path =~ s!\\\\!/!g; $workspaceUrl . $path}eg;
                    Matcher m = wsPattern.matcher(line);
                    int pos = 0;
                    while (m.find(pos)) {
                        String path = m.group(2).replace(File.separatorChar, '/');
                        String linkUrl = DESCRIPTOR.artifactMatches(path, build) ? artifactUrl : workspaceUrl;
                        // Append ' ' to make sure mail readers do not interpret following ':' as part of URL:
                        String prefix = line.substring(0, m.start()) + linkUrl + Util.encode(path) + ' ';
                        pos = prefix.length();
                        line = prefix + line.substring(m.end());
                        // XXX better style to reuse Matcher and fix offsets, but more work
                        m = wsPattern.matcher(line);
                    }
                }
                buf.append(line);
                buf.append('\n');
            }
        } catch (IOException e) {
            // somehow failed to read the contents of the log
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            buf.append("Failed to access build log\n\n").append(sw);
        }

        msg.setText(buf.toString());

        return msg;
    }

    private MimeMessage createEmptyMail(Build build, BuildListener listener) throws MessagingException {
        MimeMessage msg = new MimeMessage(DESCRIPTOR.createSession());
        // TODO: I'd like to put the URL to the page in here,
        // but how do I obtain that?
        msg.setContent("","text/plain");
        msg.setFrom(new InternetAddress(DESCRIPTOR.getAdminAddress()));
        msg.setSentDate(new Date());

        List<InternetAddress> rcp = new ArrayList<InternetAddress>();
        StringTokenizer tokens = new StringTokenizer(recipients);
        while(tokens.hasMoreTokens())
            rcp.add(new InternetAddress(tokens.nextToken()));
        if(sendToIndividuals) {
            Set<User> users = new HashSet<User>();
            for (Entry change : build.getChangeSet()) {
                User a = change.getAuthor();
                if(users.add(a)) {
                    String adrs = a.getProperty(UserProperty.class).getAddress();
                    if(adrs!=null)
                        rcp.add(new InternetAddress(adrs));
                    else {
                        listener.getLogger().println("Failed to send e-mail to "+a.getFullName()+" because no e-mail address is known, and no default e-mail domain is configured");
                    }
                }
            }
        }
        msg.setRecipients(Message.RecipientType.TO, rcp.toArray(new InternetAddress[rcp.size()]));
        return msg;
    }

    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    private String getSubject(Build build, String caption) {
        return caption +build.getProject().getName()+" #"+build.getNumber();
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
         * Null if unconfigured.
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

        /** Check whether a path (/-separated) will be archived. */
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
     * Copied from JDK5, to avoid 5.0 dependency.
     */
    private static String quoteRegexp(String s) {
        int slashEIndex = s.indexOf("\\E");
        if (slashEIndex == -1)
            return "\\Q" + s + "\\E";

        StringBuilder sb = new StringBuilder(s.length() * 2);
        sb.append("\\Q");
        int current = 0;
        while ((slashEIndex = s.indexOf("\\E", current)) != -1) {
            sb.append(s.substring(current, slashEIndex));
            current = slashEIndex + 2;
            sb.append("\\E\\\\E\\Q");
        }
        sb.append(s.substring(current, s.length()));
        sb.append("\\E");
        return sb.toString();
    }
}
