/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Bruce Chapman
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

import hudson.Launcher;
import hudson.Extension;
import hudson.maven.MavenBuild;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MavenModule;
import hudson.model.BuildListener;
import hudson.tasks.MailSender;
import hudson.tasks.Mailer;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

import net.sf.json.JSONObject;

/**
 * Sends out an e-mail notification for Maven build result.
 * @author Kohsuke Kawaguchi
 */
public class MavenMailer extends MavenReporter {
    /**
     * @see Mailer
     */
    public String recipients;
    public boolean dontNotifyEveryUnstableBuild;
    public boolean sendToIndividuals;

    public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        new MailSender(recipients,dontNotifyEveryUnstableBuild,sendToIndividuals).execute(build,listener);
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public String getDisplayName() {
            return Messages.MavenMailer_DisplayName();
        }

        public String getHelpFile() {
            return "/help/project-config/mailer.html";
        }

        // reuse the config from the mailer.
        @Override
        public String getConfigPage() {
            return getViewPage(Mailer.class,"config.jelly");
        }

        public MavenReporter newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            MavenMailer m = new MavenMailer();
            req.bindParameters(m,"mailer_");
            m.dontNotifyEveryUnstableBuild = req.getParameter("mailer_notifyEveryUnstableBuild")==null;
            return m;
        }
    }

    private static final long serialVersionUID = 1L;
}
