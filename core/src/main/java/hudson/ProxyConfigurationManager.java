/*
 * The MIT License
 *
 * Copyright (c) 2023, CloudBees Inc, and other contributors
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

package hudson;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import java.io.IOException;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

@Extension
public class ProxyConfigurationManager extends ManagementLink {

    @Override
    public String getDisplayName() {
        return Messages.ProxyConfigurationManager_DisplayName();
    }

    @Override
    public String getIconFileName() {
        return "symbol-proxy";
    }

    @Override
    public String getUrlName() {
        return "proxyConfigurationManager";
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }

    @Override
    public String getDescription() {
        return Messages.ProxyConfigurationManager_Description();
    }

    public Descriptor<ProxyConfiguration> getProxyDescriptor() {
        return Jenkins.get().getDescriptor(ProxyConfiguration.class);
    }


    @POST
    public HttpResponse doProxyConfigure(StaplerRequest req) throws IOException, ServletException {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.ADMINISTER);

        ProxyConfiguration pc = req.bindJSON(ProxyConfiguration.class, req.getSubmittedForm());
        if (pc.name == null) {
            jenkins.proxy = null;
            ProxyConfiguration.getXmlFile().delete();
        } else {
            jenkins.proxy = pc;
            jenkins.proxy.save();
        }
        return new HttpRedirect("..");
    }

}
