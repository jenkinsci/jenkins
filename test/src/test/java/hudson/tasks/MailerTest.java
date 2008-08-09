package hudson.tasks;

import org.jvnet.hudson.main.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.mock_javamail.Mailbox;

import javax.mail.internet.InternetAddress;
import javax.mail.Address;

import hudson.model.FreeStyleProject;

/**
 * @author Kohsuke Kawaguchi
 */
public class MailerTest extends HudsonTestCase {
    public MailerTest(String name) {
        super(name);
    }

    @Bug(1566)
    public void testSenderAddress() throws Exception {
        // intentionally give the whole thin in a double quote
        Mailer.DESCRIPTOR.setAdminAddress("\"me <me@sun.com>\"");

        String recipient = "you <you@sun.com>";
        Mailbox yourInbox = Mailbox.get(new InternetAddress(recipient));
        yourInbox.clear();

        // create a project to simulate a build failure
        FreeStyleProject project = (FreeStyleProject)hudson.createProject(FreeStyleProject.DESCRIPTOR, "test" );
        project.getBuildersList().add(new FailureBuilder());
        Mailer m = new Mailer();
        m.recipients = recipient;
        project.getPublishersList().add(m);

        project.scheduleBuild2(0).get();

        assertEquals(1,yourInbox.size());
        Address[] senders = yourInbox.get(0).getFrom();
        assertEquals(1,senders.length);
        assertEquals("me <me@sun.com>",senders[0].toString());
    }
}
