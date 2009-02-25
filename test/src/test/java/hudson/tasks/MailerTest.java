/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.mock_javamail.Mailbox;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlInput;

/**
 * @author Kohsuke Kawaguchi
 */
public class MailerTest extends HudsonTestCase {
    @Bug(1566)
    public void testSenderAddress() throws Exception {
        // intentionally give the whole thin in a double quote
        Mailer.descriptor().setAdminAddress("\"me <me@sun.com>\"");

        String recipient = "you <you@sun.com>";
        Mailbox yourInbox = Mailbox.get(new InternetAddress(recipient));
        yourInbox.clear();

        // create a project to simulate a build failure
        FreeStyleProject project = createFreeStyleProject();
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

    /**
     * Makes sure the use of "localhost" in the Hudson URL reports a warning.
     */
    public void testLocalHostWarning() throws Exception {
        HtmlPage p = new WebClient().goTo("configure");
        HtmlInput url = p.getFormByName("config").getInputByName("_.url");
        url.setValueAttribute("http://localhost:1234/");
        assertTrue(p.getDocumentElement().getTextContent().contains("instead of localhost"));
    }
}
