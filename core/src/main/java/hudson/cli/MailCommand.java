/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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
package hudson.cli;

import hudson.tasks.Mailer;
import hudson.Extension;
import jenkins.model.Jenkins;
import hudson.model.Item;

import javax.mail.internet.MimeMessage;
import javax.mail.Transport;

/**
 * Sends e-mail through Hudson.
 *
 * <p>
 * Various platforms have different commands to do this, so on heterogenous platform, doing this via Hudson is easier.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class MailCommand extends CLICommand {
    public String getShortDescription() {
        return Messages.MailCommand_ShortDescription();
    }

    protected int run() throws Exception {
        Jenkins.getInstance().checkPermission(Item.CONFIGURE);
        Transport.send(new MimeMessage(Mailer.descriptor().createSession(),stdin));
        return 0;
    }
}
