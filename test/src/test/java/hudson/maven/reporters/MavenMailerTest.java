/*
 * The MIT License
 *
 * Copyright (c) 2011, Dominik Bartholdi
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
package hudson.maven.reporters;

import static org.junit.Assert.assertEquals;
import hudson.maven.MavenModuleSet;
import hudson.model.Result;
import hudson.tasks.Mailer;
import hudson.tasks.Mailer.DescriptorImpl;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

import org.apache.commons.jexl.junit.Asserter;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.mock_javamail.Mailbox;

/**
 * 
 * @author imod (Dominik Bartholdi)
 * 
 */
/**
 * @author marcelo
 *
 */
public class MavenMailerTest {

	private static final String EMAIL_JENKINS_CONFIGURED = "jenkins.configured.mail@domain.org";
	private static final String EMAIL_ADMIN = "\"me <me@sun.com>\"";
	private static final String EMAIL_SOME = "some.email@domain.org";
	private static final String EMAIL_OTHER = "other.email@domain.org";
	
	@Rule public JenkinsRule j = new JenkinsRule();

	@Test
	@Bug(5695)
    public void testMulipleMails() throws Exception {

        // there is one module failing in the build, therefore we expect one mail for the failed module and one for the over all build status
        final Mailbox inbox = runMailTest(true);
        assertEquals(2, inbox.size());

    }

	@Test
    @Bug(5695)
    public void testSingleMails() throws Exception {

        final Mailbox inbox = runMailTest(false);
        assertEquals(1, inbox.size());

    }

    public Mailbox runMailTest(boolean perModuleEamil) throws Exception {

        final DescriptorImpl mailDesc = Jenkins.getInstance().getDescriptorByType(Mailer.DescriptorImpl.class);

        // intentionally give the whole thin in a double quote
        Mailer.descriptor().setAdminAddress(EMAIL_ADMIN);

        String recipient = "you <you@sun.com>";
        Mailbox yourInbox = Mailbox.get(new InternetAddress(recipient));
        yourInbox.clear();

        j.configureDefaultMaven();
        MavenModuleSet mms = j.createMavenProject();
        mms.setGoals("test");
        mms.setScm(new ExtractResourceSCM(getClass().getResource("/hudson/maven/maven-multimodule-unit-failure.zip")));
        j.assertBuildStatus(Result.UNSTABLE, mms.scheduleBuild2(0).get());

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
    
    
    /**
	 * Test using the list of recipients of TAG ciManagement defined in
	 * ModuleRoot for all the modules.
	 * 
	 * @throws Exception
	 */
    @Test
    @Bug(1201)
    public void testCiManagementNotificationRoot() throws Exception {
    	JenkinsLocationConfiguration.get().setAdminAddress(EMAIL_ADMIN);
        Mailbox yourInbox = Mailbox.get(new InternetAddress(EMAIL_SOME));
        Mailbox jenkinsConfiguredInbox = Mailbox.get(new InternetAddress(EMAIL_JENKINS_CONFIGURED));
        yourInbox.clear();
        jenkinsConfiguredInbox.clear();

        j.configureDefaultMaven();
        MavenModuleSet mms = j.createMavenProject();
        mms.setGoals("test");
        mms.setScm(new ExtractResourceSCM(getClass().getResource("/hudson/maven/JENKINS-1201-parent-defined.zip")));
        
        MavenMailer m = new MavenMailer();
        m.recipients = EMAIL_JENKINS_CONFIGURED;
        m.perModuleEmail = true;
        mms.getReporters().add(m);
        
        j.assertBuildStatus(Result.UNSTABLE, mms.scheduleBuild2(0).get());
        
        assertEquals(2, yourInbox.size());
        assertEquals(2, jenkinsConfiguredInbox.size());
        
        Message message = yourInbox.get(0);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = yourInbox.get(1);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = jenkinsConfiguredInbox.get(0);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = jenkinsConfiguredInbox.get(1);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
    }
    
    /**
	 * Test using the list of recipients of TAG ciManagement defined in
	 * ModuleRoot for de root module, and the recipients defined in moduleA for
	 * moduleA.
	 * 
	 * @throws Exception
	 */
    @Test
    @Bug(6421)
    public void testCiManagementNotificationModule() throws Exception {
    	
    	JenkinsLocationConfiguration.get().setAdminAddress(EMAIL_ADMIN);
        Mailbox otherInbox = Mailbox.get(new InternetAddress(EMAIL_OTHER));
        Mailbox someInbox = Mailbox.get(new InternetAddress(EMAIL_SOME));
        Mailbox jenkinsConfiguredInbox = Mailbox.get(new InternetAddress(EMAIL_JENKINS_CONFIGURED));
        otherInbox.clear();
        someInbox.clear();
        jenkinsConfiguredInbox.clear();

        j.configureDefaultMaven();
        MavenModuleSet mms = j.createMavenProject();
        mms.setGoals("test");
        mms.setScm(new ExtractResourceSCM(getClass().getResource("/hudson/maven/JENKINS-1201-module-defined.zip")));
        MavenMailer m = new MavenMailer();
        m.recipients = EMAIL_JENKINS_CONFIGURED;
        m.perModuleEmail = true;
        mms.getReporters().add(m);
        
        j.assertBuildStatus(Result.FAILURE, mms.scheduleBuild2(0).get());

        assertEquals(1, otherInbox.size());
        assertEquals(1, someInbox.size());
        assertEquals(2, jenkinsConfiguredInbox.size());
        
        Message message = otherInbox.get(0);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_OTHER, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = someInbox.get(0);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_SOME, message);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = jenkinsConfiguredInbox.get(0);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
        message = jenkinsConfiguredInbox.get(1);
        assertEquals(2, message.getAllRecipients().length);
        assertContainsRecipient(EMAIL_JENKINS_CONFIGURED, message);
        
    }

	private void assertContainsRecipient(String email, Message message) throws Exception {
		assert email != null;
		assert !email.trim().equals("");
		boolean containRecipient = false;
		for (Address address: message.getAllRecipients()) {
			if (email.equals(address.toString())) {
				containRecipient = true;
				break;
			}
		}
		assert containRecipient;
	}

}
