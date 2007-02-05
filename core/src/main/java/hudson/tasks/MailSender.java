package hudson.tasks;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.User;
import hudson.model.AbstractProject;
import hudson.Util;
import hudson.FilePath;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import javax.mail.internet.MimeMessage;
import javax.mail.internet.InternetAddress;
import javax.mail.Address;
import javax.mail.Transport;
import javax.mail.MessagingException;
import javax.mail.Message;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * Core logic of sending out notification e-mail.
 *
 * @author Jesse Glick
 * @author Kohsuke Kawaguchi
 */
public class MailSender<P extends AbstractProject<P,B>,B extends AbstractBuild<P,B>> {
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

    public boolean execute(B build, BuildListener listener) throws InterruptedException {
        try {
            MimeMessage mail = getMail(build, listener);
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

    private MimeMessage getMail(B build, BuildListener listener) throws MessagingException, InterruptedException {
        if(build.getResult()== Result.FAILURE) {
            return createFailureMail(build, listener);
        }

        if(build.getResult()==Result.UNSTABLE) {
            B prev = build.getPreviousBuild();
            if(!dontNotifyEveryUnstableBuild)
                return createUnstableMail(build, listener);
            if(prev!=null) {
                if(prev.getResult()==Result.SUCCESS)
                    return createUnstableMail(build, listener);
            }
        }

        if(build.getResult()==Result.SUCCESS) {
            B prev = build.getPreviousBuild();
            if(prev!=null) {
                if(prev.getResult()==Result.FAILURE)
                    return createBackToNormalMail(build, "normal", listener);
                if(prev.getResult()==Result.UNSTABLE)
                    return createBackToNormalMail(build, "stable", listener);
            }
        }

        return null;
    }

    private MimeMessage createBackToNormalMail(B build, String subject, BuildListener listener) throws MessagingException {
        MimeMessage msg = createEmptyMail(build, listener);

        msg.setSubject(getSubject(build,"Hudson build is back to "+subject +": "));
        StringBuffer buf = new StringBuffer();
        appendBuildUrl(build,buf);
        msg.setText(buf.toString());

        return msg;
    }

    private MimeMessage createUnstableMail(B build, BuildListener listener) throws MessagingException {
        MimeMessage msg = createEmptyMail(build, listener);

        msg.setSubject(getSubject(build,"Hudson build became unstable: "));
        StringBuffer buf = new StringBuffer();
        appendBuildUrl(build,buf);
        msg.setText(buf.toString());

        return msg;
    }

    private void appendBuildUrl(B build, StringBuffer buf) {
        String baseUrl = Mailer.DESCRIPTOR.getUrl();
        if(baseUrl!=null) {
            buf.append("See ").append(baseUrl).append(Util.encode(build.getUrl())).append("\n\n");
        }
    }

    private MimeMessage createFailureMail(B build, BuildListener listener) throws MessagingException, InterruptedException {
        MimeMessage msg = createEmptyMail(build, listener);

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
                    quoteRegexp(ws.getRemote()) + "|" + quoteRegexp(ws.toURI().toString()) + ")[/\\\\]?([^:#\\s]*)");
            }
            for (int i = start; i < lines.length; i++) {
                String line = lines[i];
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
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            buf.append("Failed to access build log\n\n").append(sw);
        }

        msg.setText(buf.toString());

        return msg;
    }

    private MimeMessage createEmptyMail(B build, BuildListener listener) throws MessagingException {
        MimeMessage msg = new MimeMessage(Mailer.DESCRIPTOR.createSession());
        // TODO: I'd like to put the URL to the page in here,
        // but how do I obtain that?
        msg.setContent("","text/plain");
        msg.setFrom(new InternetAddress(Mailer.DESCRIPTOR.getAdminAddress()));
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
                    String adrs = a.getProperty(Mailer.UserProperty.class).getAddress();
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

    private String getSubject(B build, String caption) {
        return caption +build.getProject().getName()+" #"+build.getNumber();
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

    /** Check whether a path (/-separated) will be archived. */
    protected boolean artifactMatches(String path, B build) {
        return false;
    }


    private static final Logger LOGGER = Logger.getLogger(MailSender.class.getName());

    private static final int MAX_LOG_LINES = 250;
}
