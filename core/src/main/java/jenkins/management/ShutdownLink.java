/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Intl., Nicolas De loof
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

package jenkins.management;

import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.NonNull;

@Extension(ordinal = Integer.MIN_VALUE)
@Symbol("prepareQuietDown")
public class ShutdownLink extends ManagementLink {

    private static final Logger LOGGER = Logger.getLogger(ShutdownLink.class.getName());

    @Override
    public String getIconFileName() {
        return "system-log-out.png";
    }

    public String getDisplayName() {
        return Jenkins.get().isQuietingDown() ? Messages.ShutdownLink_DisplayName_update() : Messages.ShutdownLink_DisplayName_prepare();
    }

    @Override
    public String getDescription() {
        return Jenkins.get().isQuietingDown() ? Messages.ShutdownLink_ShuttingDownInProgressDescription() : Messages.ShutdownLink_Description();
    }

    @Override
    public String getUrlName() {
        return "prepareShutdown";
    }

    @POST
    public synchronized void doPrepare(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        JSONObject submittedForm = req.getSubmittedForm();
        String inputReason = submittedForm.getString("shutdownReason");
        String shutdownReason = inputReason.isEmpty() ? null : inputReason;
        LOGGER.log(Level.FINE, "Shutdown requested by user {0}", Jenkins.getAuthentication().getName());
        Jenkins.get().doQuietDown(false, 0, shutdownReason).generateResponse(req, rsp, null);
    }

    @POST
    public synchronized void doCancel(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.MANAGE);

        LOGGER.log(Level.FINE, "Shutdown cancel requested by user {0}", Jenkins.getAuthentication().getName());
        Jenkins.get().doCancelQuietDown().generateResponse(req, rsp, null);
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.MANAGE;
    }
  
    @NonNull
    @Override
    public Category getCategory() {
        return Category.TOOLS;
    }
}
