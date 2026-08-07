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

package hudson.security.csrf;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Show the crumb configuration to the system config page.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = 195) @Symbol("crumb") // immediately after the security setting
public class GlobalCrumbIssuerConfiguration extends GlobalConfiguration {
    @Override
    public @NonNull GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        // for compatibility reasons, the actual value is stored in Jenkins
        Jenkins j = Jenkins.get();

        if (json.has("crumbIssuer")) {
            j.setCrumbIssuer(req.bindJSON(CrumbIssuer.class, json.getJSONObject("crumbIssuer")));
        } else {
            j.setCrumbIssuer(createDefaultCrumbIssuer());
        }

        return true;
    }

    @Restricted(NoExternalUse.class) // Jelly
    public CrumbIssuer getCrumbIssuer() {
        return Jenkins.get().getCrumbIssuer();
    }

    @Restricted(NoExternalUse.class)
    public static CrumbIssuer createDefaultCrumbIssuer() {
        if (DISABLE_CSRF_PROTECTION) {
            return null;
        }
        return new DefaultCrumbIssuer();
    }

    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* non-final */ boolean DISABLE_CSRF_PROTECTION = SystemProperties.getBoolean(GlobalCrumbIssuerConfiguration.class.getName() + ".DISABLE_CSRF_PROTECTION");
}
