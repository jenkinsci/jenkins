package hudson.maven.reporters;

import hudson.maven.MavenModuleSet;
import hudson.model.Result;
import hudson.tasks.Mailer;
import hudson.tasks.Mailer.DescriptorImpl;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;

import jenkins.model.Jenkins;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.mock_javamail.Mailbox;

public class MavenMailerTest extends HudsonTestCase {

    @Bug(5695)
    public void testMulipleMails() throws Exception {

        final Mailbox inbox = runMailTest(true);
        assertEquals(2, inbox.size());

    }

    @Bug(5695)
    public void testSingleMails() throws Exception {

        final Mailbox inbox = runMailTest(false);
        assertEquals(1, inbox.size());

    }

    public Mailbox runMailTest(boolean perModuleEamil) throws Exception {

        final DescriptorImpl mailDesc = Jenkins.getInstance().getDescriptorByType(Mailer.DescriptorImpl.class);

        // intentionally give the whole thin in a double quote
        Mailer.descriptor().setAdminAddress("\"me <me@sun.com>\"");

        String recipient = "you <you@sun.com>";
        Mailbox yourInbox = Mailbox.get(new InternetAddress(recipient));
        yourInbox.clear();

        configureDefaultMaven();
        MavenModuleSet mms = createMavenProject();
        mms.setGoals("test");
        mms.setScm(new ExtractResourceSCM(getClass().getResource("/hudson/maven/maven-multimodule-unit-failure.zip")));
        assertBuildStatus(Result.UNSTABLE, mms.scheduleBuild2(0).get());

        MavenMailer m = new MavenMailer();
        m.recipients = recipient;
        m.perModuleEmail = perModuleEamil;
        mms.getReporters().add(m);

        mms.scheduleBuild2(0).get();

        Address[] senders = yourInbox.get(0).getFrom();
        assertEquals(1, senders.length);
        assertEquals("me <me@sun.com>", senders[0].toString());

        return yourInbox;
    }

}
