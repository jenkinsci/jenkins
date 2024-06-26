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
import java.io.IOException;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

@Extension @Restricted(NoExternalUse.class)
public class ProxyConfigurationManager extends GlobalConfiguration {

    @NonNull
    @Override
    public String getDisplayName() {
        return Messages.ProxyConfigurationManager_DisplayName();
    }

    public Descriptor<ProxyConfiguration> getProxyDescriptor() {
        return Jenkins.get().getDescriptor(ProxyConfiguration.class);
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        ProxyConfiguration pc = req.bindJSON(ProxyConfiguration.class, json);
        try {
            saveProxyConfiguration(pc);
        } catch (IOException e) {
            throw new FormException(e.getMessage(), e, null);
        }
        return true;
    }

    public static void saveProxyConfiguration(ProxyConfiguration pc) throws IOException {
        Jenkins jenkins = Jenkins.get();
        if (pc.name == null) {
            jenkins.proxy = null;
            ProxyConfiguration.getXmlFile().delete();
        } else {
            jenkins.proxy = pc;
            jenkins.proxy.save();
        }
    }

}
