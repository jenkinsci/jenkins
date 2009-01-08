package hudson.tasks;

import hudson.FilePath;
import hudson.Util;
import hudson.Functions;
import hudson.model.*;
import hudson.scm.ChangeLogSet;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core logic of sending out notification e-mail.
 *
 * @author Jesse Glick
 * @author Kohsuke Kawaguchi
 */
public class MailSender {
    /**
     * Whitespace-separated list of e-mail addresses that represent recipients.
     */
    private String recipients;

    /**
     * If true, only the first unstable build will be reported.
     */
    private boolean dontNotifyEveryUnstableBuild;

    /**
     * If true, individuals will receive e-mails regarding who broke the build.
     */
    private boolean sendToIndividuals;


    public MailSender(String recipients, boolean dontNotifyEveryUnstableBuild, boolean sendToIndividuals) {
        this.recipients = recipients;
        this.dontNotifyEveryUnstableBuild = dontNotifyEveryUnstableBuild;
        this.sendToIndividuals = sendToIndividuals;
    }

    public boolean execute(AbstractBuild<?, ?> build, BuildListener listener) throws InterruptedException {
        try {
            MimeMessage mail = getMail(build, listener);
            if (mail != null) {
                // if the previous e-mail was sent for a success, this new e-mail
                // is not a follow up
                AbstractBuild<?, ?> pb = build.getPreviousBuild();
                if(pb!=null && pb.getResult()==Result.SUCCESS) {
                    mail.removeHeader("In-Reply-To");
                    mail.removeHeader("References");
                }

                Address[] allRecipients = mail.getAllRecipients();
                if (allRecipients != null) {
                    StringBuffer buf = new StringBuffer("Sending e-mails to:");
                    for (Address a : allRecipients)
                        buf.append(' ').append(a);
                    listener.getLogger().println(buf);
                    Transport.send(mail);

                    build.addAction(new MailMessageIdAction(mail.getMessageID()));
                } else {
                    listener.getLogger().println(Messages.MailSender_ListEmpty());
                }
            }
        } catch (MessagingException e) {
            e.printStackTrace(listener.error(e.getMessage()));
        }

        return true;
    }

    protected MimeMessage getMail(AbstractBuild<?, ?> build, BuildListener listener) throws MessagingException, InterruptedException {
        if (build.getResult() == Result.FAILURE) {
            return createFailureMail(build, listener);
        }

        if (build.getResult() == Result.UNSTABLE) {
            AbstractBuild<?, ?> prev = build.getPreviousBuild();
            if (!dontNotifyEveryUnstableBuild)
                return createUnstableMail(build, listener);
            if (prev != null) {
                if (prev.getResult() == Result.SUCCESS)
                    return createUnstableMail(build, listener);
            }
        }

        if (build.getResult() == Result.SUCCESS) {
            AbstractBuild<?, ?> prev = build.getPreviousBuild();
            if (prev != null) {
                if (prev.getResult() == Result.FAILURE)
                    return createBackToNormalMail(build, "normal", listener);
                if (prev.getResult() == Result.UNSTABLE)
                    return createBackToNormalMail(build, "stable", listener);
            }
        }

        return null;
    }

    private MimeMessage createBackToNormalMail(AbstractBuild<?, ?> build, String subject, BuildListener listener) throws MessagingException {
        MimeMessage msg = createEmptyMail(build, listener);

        msg.setSubject(getSubject(build, "Hudson build is back to " + subject + ": "),"UTF-8");
        StringBuffer buf = new StringBuffer();
        appendBuildUrl(build, buf);
        msg.setText(buf.toString());

        return msg;
    }

    private MimeMessage createUnstableMail(AbstractBuild<?, ?> build, BuildListener listener) throws MessagingException {
        MimeMessage msg = createEmptyMail(build, listener);

        String subject = "Hudson build is unstable: ";

        AbstractBuild<?, ?> prev = build.getPreviousBuild();
        if(prev!=null) {
            if(prev.getResult()==Result.SUCCESS)
                subject = "Hudson build became unstable: ";
            if(prev.getResult()==Result.UNSTABLE)
                subject = "Hudson build is still unstable: ";
        }

        msg.setSubject(getSubject(build, subject),"UTF-8");
        StringBuffer buf = new StringBuffer();
        appendBuildUrl(build, buf);
        msg.setText(buf.toString());

        return msg;
    }

    private void appendBuildUrl(AbstractBuild<?, ?> build, StringBuffer buf) {
        String baseUrl = Mailer.DESCRIPTOR.getUrl();
        if (baseUrl != null) {
            buf.append("See ").append(baseUrl).append(Util.encode(build.getUrl())).append("changes\n\n");
        }
    }

    private MimeMessage createFailureMail(AbstractBuild<?, ?> build, BuildListener listener) throws MessagingException, InterruptedException {
        MimeMessage msg = createEmptyMail(build, listener);

        msg.setSubject(getSubject(build, "Build failed in Hudson: "),"UTF-8");

        StringBuffer buf = new StringBuffer();
        appendBuildUrl(build, buf);

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
            // Restrict max log size to avoid sending enormous logs over email.
            // Interested users can always look at the log on the web server.
            List<String> lines = build.getLog(MAX_LOG_LINES);

            String workspaceUrl = null, artifactUrl = null;
            Pattern wsPattern = null;
            String baseUrl = Mailer.DESCRIPTOR.getUrl();
            if (baseUrl != null) {
                // Hyperlink local file paths to the repository workspace or build artifacts.
                // Note that it is possible for a failure mail to refer to a file using a workspace
                // URL which has already been corrected in a subsequent build. To fix, archive.
                workspaceUrl = baseUrl + Util.encode(build.getProject().getUrl()) + "ws/";
                artifactUrl = baseUrl + Util.encode(build.getUrl()) + "artifact/";
                FilePath ws = build.getProject().getWorkspace();
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
                    Pattern.quote(ws.getRemote()) + "|" + Pattern.quote(ws.toURI().toString()) + ")[/\\\\]?([^:#\\s]*)");
            }
            for (String line : lines) {
                if (wsPattern != null) {
                    // Perl: $line =~ s{$rx}{$path = $2; $path =~ s!\\\\!/!g; $workspaceUrl . $path}eg;
                    Matcher m = wsPattern.matcher(line);
                    int pos = 0;
                    while (m.find(pos)) {
                        String path = m.group(2).replace(File.separatorChar, '/');
                        String linkUrl = artifactMatches(path, build) ? artifactUrl : workspaceUrl;
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
            buf.append("Failed to access build log\n\n").append(Functions.printThrowable(e));
        }

        msg.setText(buf.toString());

        return msg;
    }

    private MimeMessage createEmptyMail(AbstractBuild<?, ?> build, BuildListener listener) throws MessagingException {
        MimeMessage msg = new MimeMessage(Mailer.DESCRIPTOR.createSession());
        // TODO: I'd like to put the URL to the page in here,
        // but how do I obtain that?
        msg.setContent("", "text/plain");
        msg.setFrom(new InternetAddress(Mailer.DESCRIPTOR.getAdminAddress()));
        msg.setSentDate(new Date());

        Set<InternetAddress> rcp = new LinkedHashSet<InternetAddress>();
        StringTokenizer tokens = new StringTokenizer(recipients);
        while (tokens.hasMoreTokens())
            rcp.add(new InternetAddress(tokens.nextToken()));
        if (sendToIndividuals) {
            Set<User> culprits = build.getCulprits();

            if(debug)
                listener.getLogger().println("Trying to send e-mails to individuals who broke the build. sizeof(culprits)=="+culprits.size());

            for (User a : culprits) {
                String adrs = Util.fixEmpty(a.getProperty(Mailer.UserProperty.class).getAddress());
                if(debug)
                    listener.getLogger().println("  User "+a.getId()+" -> "+adrs);
                if (adrs != null)
                    rcp.add(new InternetAddress(adrs));
                else {
                    listener.getLogger().println(Messages.MailSender_NoAddress(a.getFullName()));
                }
            }
        }
        msg.setRecipients(Message.RecipientType.TO, rcp.toArray(new InternetAddress[rcp.size()]));

        AbstractBuild<?, ?> pb = build.getPreviousBuild();
        if(pb!=null) {
            MailMessageIdAction b = pb.getAction(MailMessageIdAction.class);
            if(b!=null) {
                msg.setHeader("In-Reply-To",b.messageId);
                msg.setHeader("References",b.messageId);
            }
        }

        return msg;
    }

    private String getSubject(AbstractBuild<?, ?> build, String caption) {
        return caption + build.getProject().getFullDisplayName() + " #" + build.getNumber();
    }

    /**
     * Check whether a path (/-separated) will be archived.
     */
    protected boolean artifactMatches(String path, AbstractBuild<?, ?> build) {
        return false;
    }

    private static final Logger LOGGER = Logger.getLogger(MailSender.class.getName());

    public static boolean debug = false;

    private static final int MAX_LOG_LINES = 250;
}
