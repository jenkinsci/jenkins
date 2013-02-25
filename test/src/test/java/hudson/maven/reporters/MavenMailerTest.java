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

/**
 * 
 * @author imod (Dominik Bartholdi)
 * 
 */
public class MavenMailerTest extends HudsonTestCase {

    @Bug(5695)
    public void testMulipleMails() throws Exception {

        // there is one module failing in the build, therefore we expect one mail for the failed module and one for the over all build status
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
