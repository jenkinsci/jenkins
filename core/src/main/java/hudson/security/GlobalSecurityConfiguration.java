/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package hudson.security;

import hudson.BulkChange;
import hudson.Extension;
import hudson.markup.MarkupFormatter;
import hudson.model.Descriptor.FormException;
import hudson.model.ManagementLink;
import hudson.util.FormApply;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.util.ServerTcpPort;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Security configuration.
 * 
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = Integer.MAX_VALUE - 210)
public class GlobalSecurityConfiguration extends ManagementLink {
    
    private static final Logger LOGGER = Logger.getLogger(GlobalSecurityConfiguration.class.getName());

    public MarkupFormatter getMarkupFormatter() {
        return Jenkins.getInstance().getMarkupFormatter();
    }

    public int getSlaveAgentPort() {
        return Jenkins.getInstance().getSlaveAgentPort();
    }

    public synchronized void doConfigure(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        // for compatibility reasons, the actual value is stored in Jenkins
        BulkChange bc = new BulkChange(Jenkins.getInstance());
        try{
            boolean result = configure(req, req.getSubmittedForm());
            LOGGER.log(Level.FINE, "security saved: "+result);
            Jenkins.getInstance().save();
            FormApply.success(req.getContextPath()+"/manage").generateResponse(req, rsp, null);
        } finally {
            bc.commit();
        }
    }

    public boolean configure(StaplerRequest req, JSONObject json) throws hudson.model.Descriptor.FormException {
        // for compatibility reasons, the actual value is stored in Jenkins
        Jenkins j = Jenkins.getInstance();
        j.checkPermission(Jenkins.ADMINISTER);
        if (json.has("useSecurity")) {
            JSONObject security = json.getJSONObject("useSecurity");
            j.setSecurityRealm(SecurityRealm.all().newInstanceFromRadioList(security, "realm"));
            j.setAuthorizationStrategy(AuthorizationStrategy.all().newInstanceFromRadioList(security, "authorization"));
            try {
                j.setSlaveAgentPort(new ServerTcpPort(security.getJSONObject("slaveAgentPort")).getPort());
            } catch (IOException e) {
                throw new hudson.model.Descriptor.FormException(e, "slaveAgentPortType");
            }
            if (security.has("markupFormatter")) {
                j.setMarkupFormatter(req.bindJSON(MarkupFormatter.class, security.getJSONObject("markupFormatter")));
            } else {
                j.setMarkupFormatter(null);
            }
        } else {
            j.disableSecurity();
        }
        // return true for backward compatibility reasons
        return true;
    }

    @Override
    public String getDisplayName() {
        return Messages.GlobalSecurityConfiguration_DisplayName();
    }
    
    @Override
    public String getDescription() {
        return Messages.GlobalSecurityConfiguration_Description();
    }

    @Override
    public String getIconFileName() {
        return "lock.png";
    }

    @Override
    public String getUrlName() {
        return "globalSecurity";
    }
    
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }
}
