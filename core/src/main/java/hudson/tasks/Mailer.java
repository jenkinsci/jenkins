/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Bruce Chapman, Erik Ramfelt, Jean-Baptiste Quenot, Luca Domenico Milanesio
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
package hudson.tasks;

import hudson.Launcher;
import hudson.Functions;
import hudson.Extension;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import static hudson.Util.fixEmptyAndTrim;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.ParametersAction;
import hudson.model.User;
import hudson.model.UserPropertyDescriptor;
import hudson.model.Hudson;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.XStream2;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import com.thoughtworks.xstream.converters.UnmarshallingContext;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.InetAddress;
import java.net.UnknownHostException;

import net.sf.json.JSONObject;

/**
 * {@link Publisher} that sends the build result in e-mail.
 *
 * @author Kohsuke Kawaguchi
 */
public class Mailer extends Notifier {
    protected static final Logger LOGGER = Logger.getLogger(Mailer.class.getName());

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
    // in XStream.  Deprecated since 2005-04-23.
    private transient String from;
    private transient String subject;
    private transient boolean failureOnly;
    private transient String charset;

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        if(debug)
            listener.getLogger().println("Running mailer");
        // substitute build parameters if available
        ParametersAction parameters = build.getAction(ParametersAction.class);
        if (parameters!=null)
            recipients = parameters.substitute(build, recipients);
            
        return new MailSender(recipients,dontNotifyEveryUnstableBuild,sendToIndividuals, descriptor().getCharset()) {
            /** Check whether a path (/-separated) will be archived. */
            @Override
            public boolean artifactMatches(String path, AbstractBuild<?,?> build) {
                ArtifactArchiver aa = build.getProject().getPublishersList().get(ArtifactArchiver.class);
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

    /**
     * This class does explicit check pointing.
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * @deprecated as of 1.286
     *      Use {@link #descriptor()} to obtain the current instance.
     */
    @Restricted(NoExternalUse.class)
    @RestrictedSince("1.355")
    public static DescriptorImpl DESCRIPTOR;

    public static DescriptorImpl descriptor() {
        return Hudson.getInstance().getDescriptorByType(Mailer.DescriptorImpl.class);
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
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
        private String smtpAuthUsername;

        private Secret smtpAuthPassword;

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
         * If true use SSL on port 465 (standard SMTPS) unless <code>smtpPort</code> is set.
         */
        private boolean useSsl;

        /**
         * The SMTP port to use for sending e-mail. Null for default to the environment,
         * which is usually <tt>25</tt>.
         */
        private String smtpPort;

        /**
         * The charset to use for the text and subject.
         */
        private String charset;
        
        /**
         * Used to keep track of number test e-mails.
         */
        private static transient int testEmailCount = 0;
        

        public DescriptorImpl() {
            load();
            DESCRIPTOR = this;
        }

        public String getDisplayName() {
            return Messages.Mailer_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/project-config/mailer.html";
        }

        public String getDefaultSuffix() {
            return defaultSuffix;
        }

        /** JavaMail session. */
        public Session createSession() {
            return createSession(smtpHost,smtpPort,useSsl,smtpAuthUsername,smtpAuthPassword);
        }
        private static Session createSession(String smtpHost, String smtpPort, boolean useSsl, String smtpAuthUserName, Secret smtpAuthPassword) {
            smtpPort = fixEmptyAndTrim(smtpPort);
            smtpAuthUserName = fixEmptyAndTrim(smtpAuthUserName);

            Properties props = new Properties(System.getProperties());
            if(fixEmptyAndTrim(smtpHost)!=null)
                props.put("mail.smtp.host",smtpHost);
            if (smtpPort!=null) {
                props.put("mail.smtp.port", smtpPort);
            }
            if (useSsl) {
            	/* This allows the user to override settings by setting system properties but
            	 * also allows us to use the default SMTPs port of 465 if no port is already set.
            	 * It would be cleaner to use smtps, but that's done by calling session.getTransport()...
            	 * and thats done in mail sender, and it would be a bit of a hack to get it all to
            	 * coordinate, and we can make it work through setting mail.smtp properties.
            	 */
            	if (props.getProperty("mail.smtp.socketFactory.port") == null) {
                    String port = smtpPort==null?"465":smtpPort;
                    props.put("mail.smtp.port", port);
                    props.put("mail.smtp.socketFactory.port", port);
            	}
            	if (props.getProperty("mail.smtp.socketFactory.class") == null) {
            		props.put("mail.smtp.socketFactory.class","javax.net.ssl.SSLSocketFactory");
            	}
				props.put("mail.smtp.socketFactory.fallback", "false");
			}
            if(smtpAuthUserName!=null)
                props.put("mail.smtp.auth","true");

            // avoid hang by setting some timeout. 
            props.put("mail.smtp.timeout","60000");
            props.put("mail.smtp.connectiontimeout","60000");

            return Session.getInstance(props,getAuthenticator(smtpAuthUserName,Secret.toString(smtpAuthPassword)));
        }

        private static Authenticator getAuthenticator(final String smtpAuthUserName, final String smtpAuthPassword) {
            if(smtpAuthUserName==null)    return null;
            return new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpAuthUserName,smtpAuthPassword);
                }
            };
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            // this code is brain dead
            smtpHost = nullify(json.getString("smtpServer"));
            setAdminAddress(json.getString("adminAddress"));

            defaultSuffix = nullify(json.getString("defaultSuffix"));
            String url = nullify(json.getString("url"));
            if(url!=null && !url.endsWith("/"))
                url += '/';
            hudsonUrl = url;

            if(json.has("useSMTPAuth")) {
                JSONObject auth = json.getJSONObject("useSMTPAuth");
                smtpAuthUsername = nullify(auth.getString("smtpAuthUserName"));
                smtpAuthPassword = Secret.fromString(nullify(auth.getString("smtpAuthPassword")));
            } else {
                smtpAuthUsername = null;
                smtpAuthPassword = null;
            }
            smtpPort = nullify(json.getString("smtpPort"));
            useSsl = json.getBoolean("useSsl");
            charset = json.getString("charset");
            if (charset == null || charset.length() == 0)
            	charset = "UTF-8";
            
            save();
            return true;
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
            if(v==null)     v = Messages.Mailer_Address_Not_Configured();
            return v;
        }

        public String getUrl() {
            return hudsonUrl;
        }

        public String getSmtpAuthUserName() {
            return smtpAuthUsername;
        }

        public String getSmtpAuthPassword() {
            if (smtpAuthPassword==null) return null;
            return Secret.toString(smtpAuthPassword);
        }
        
        public boolean getUseSsl() {
        	return useSsl;
        }

        public String getSmtpPort() {
        	return smtpPort;
        }
        
        public String getCharset() {
        	String c = charset;
        	if (c == null || c.length() == 0)	c = "UTF-8";
        	return c;
        }

        public void setDefaultSuffix(String defaultSuffix) {
            this.defaultSuffix = defaultSuffix;
        }

        public void setHudsonUrl(String hudsonUrl) {
            this.hudsonUrl = hudsonUrl;
        }

        public void setAdminAddress(String adminAddress) {
            if(adminAddress.startsWith("\"") && adminAddress.endsWith("\"")) {
                // some users apparently quote the whole thing. Don't konw why
                // anyone does this, but it's a machine's job to forgive human mistake
                adminAddress = adminAddress.substring(1,adminAddress.length()-1);
            }
            this.adminAddress = adminAddress;
        }

        public void setSmtpHost(String smtpHost) {
            this.smtpHost = smtpHost;
        }

        public void setUseSsl(boolean useSsl) {
            this.useSsl = useSsl;
        }

        public void setSmtpPort(String smtpPort) {
            this.smtpPort = smtpPort;
        }
        
        public void setCharset(String chaset) {
            this.charset = chaset;
        }

        public void setSmtpAuth(String userName, String password) {
            this.smtpAuthUsername = userName;
            this.smtpAuthPassword = Secret.fromString(password);
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) {
            Mailer m = new Mailer();
            req.bindParameters(m,"mailer_");
            m.dontNotifyEveryUnstableBuild = req.getParameter("mailer_notifyEveryUnstableBuild")==null;

            if(hudsonUrl==null) {
                // if Hudson URL is not configured yet, infer some default
                hudsonUrl = Functions.inferHudsonURL(req);
                save();
            }

            return m;
        }

        /**
         * Checks the URL in <tt>global.jelly</tt>
         */
        public FormValidation doCheckUrl(@QueryParameter String value) {
            if(value.startsWith("http://localhost"))
                return FormValidation.warning(Messages.Mailer_Localhost_Error());
            return FormValidation.ok();
        }

        public FormValidation doAddressCheck(@QueryParameter String value) {
            try {
                new InternetAddress(value);
                return FormValidation.ok();
            } catch (AddressException e) {
                return FormValidation.error(e.getMessage());
            }
        }

        public FormValidation doCheckSmtpServer(@QueryParameter String value) {
            try {
                if (fixEmptyAndTrim(value)!=null)
                    InetAddress.getByName(value);
                return FormValidation.ok();
            } catch (UnknownHostException e) {
                return FormValidation.error(Messages.Mailer_Unknown_Host_Name()+value);
            }
        }

        public FormValidation doCheckAdminAddress(@QueryParameter String value) {
            return doAddressCheck(value);
        }

        public FormValidation doCheckDefaultSuffix(@QueryParameter String value) {
            if (value.matches("@[A-Za-z0-9.\\-]+") || fixEmptyAndTrim(value)==null)
                return FormValidation.ok();
            else
                return FormValidation.error(Messages.Mailer_Suffix_Error());
        }

        /**
         * Send an email to the admin address
         * @throws IOException
         * @throws ServletException
         * @throws InterruptedException
         */
        public FormValidation doSendTestMail(
                @QueryParameter String smtpServer, @QueryParameter String adminAddress, @QueryParameter boolean useSMTPAuth,
                @QueryParameter String smtpAuthUserName, @QueryParameter String smtpAuthPassword,
                @QueryParameter boolean useSsl, @QueryParameter String smtpPort) throws IOException, ServletException, InterruptedException {
            try {
                if (!useSMTPAuth)   smtpAuthUserName = smtpAuthPassword = null;
                
                MimeMessage msg = new MimeMessage(createSession(smtpServer,smtpPort,useSsl,smtpAuthUserName,Secret.fromString(smtpAuthPassword)));
                msg.setSubject("Test email #" + ++testEmailCount);
                msg.setContent("This is test email #" + testEmailCount + " sent from Hudson Continuous Integration server.", "text/plain");
                msg.setFrom(new InternetAddress(adminAddress));
                msg.setSentDate(new Date());
                msg.setRecipient(Message.RecipientType.TO, new InternetAddress(adminAddress));

                Transport.send(msg);
                
                return FormValidation.ok("Email was successfully sent");
            } catch (MessagingException e) {
                return FormValidation.errorWithMarkup("<p>Failed to send out e-mail</p><pre>"+Util.escape(Functions.printThrowable(e))+"</pre>");
            }
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    /**
     * Per user property that is e-mail address.
     */
    public static class UserProperty extends hudson.model.UserProperty {
        /**
         * The user's e-mail address.
         * Null to leave it to default.
         */
        private final String emailAddress;

        public UserProperty(String emailAddress) {
            this.emailAddress = emailAddress;
        }

        @Exported
        public String getAddress() {
            if(emailAddress!=null)
                return emailAddress;

            // try the inference logic
            return MailAddressResolver.resolve(user);
        }

        @Extension
        public static final class DescriptorImpl extends UserPropertyDescriptor {
            public String getDisplayName() {
                return Messages.Mailer_UserProperty_DisplayName();
            }

            public UserProperty newInstance(User user) {
                return new UserProperty(null);
            }

            @Override
            public UserProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                return new UserProperty(req.getParameter("email.address"));
            }
        }
    }

    /**
     * Debug probe point to be activated by the scripting console.
     */
    public static boolean debug = false;

    public static class ConverterImpl extends XStream2.PassthruConverter<Mailer> {
        public ConverterImpl(XStream2 xstream) { super(xstream); }
        @Override protected void callback(Mailer m, UnmarshallingContext context) {
            if (m.from != null || m.subject != null || m.failureOnly || m.charset != null)
                OldDataMonitor.report(context, "1.10");
        }
    }
}
