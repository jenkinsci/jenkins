/*
 * The MIT License
 *
 * Copyright (c) 2011-2012, CloudBees, Inc., Daniel Khodaparast
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

import hudson.Extension;
import hudson.markup.MarkupFormatter;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.util.ServerTcpPort;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Security configuration.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=200)
public class GlobalSecurityConfiguration extends GlobalConfiguration {

	public MarkupFormatter getMarkupFormatter() {
        return Jenkins.getInstance().getMarkupFormatter();
    }
    
    public int getSlaveAgentPort() {
        return Jenkins.getInstance().getSlaveAgentPort();
    }
    
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        // for compatibility reasons, the actual value is stored in Jenkins
        Jenkins j = Jenkins.getInstance();

        if (json.has("useSecurity")) {
            JSONObject security = json.getJSONObject("useSecurity");
            j.setSecurityRealm(SecurityRealm.all().newInstanceFromRadioList(security, "realm"));
            j.setAuthorizationStrategy(AuthorizationStrategy.all().newInstanceFromRadioList(security, "authorization"));

            try {
                j.setSlaveAgentPort(new ServerTcpPort(security.getJSONObject("slaveAgentPort")).getPort());
            } catch (IOException e) {
                throw new FormException(e,"slaveAgentPortType");
            }

            if (security.has("markupFormatter")) {
                j.setMarkupFormatter(req.bindJSON(MarkupFormatter.class, security.getJSONObject("markupFormatter")));
            } else {
                j.setMarkupFormatter(null);
            }

            try {
                j.setUseCommitNames(security.has("useCommitNames") ? true : null);
            } catch (IOException e) {
                throw new FormException(e,"useCommitNames");
            }
        } else {
            j.disableSecurity();
        }

        return true;
    }
}

