/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

package jenkins.security.csp.impl;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AdministrativeMonitor;
import java.io.IOException;
import jenkins.security.csp.CspHeaderDecider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * This administrative monitor informs admins that CSP is now enforced by default.
 * It is shown when {@link CspConfiguration#enforce} is {@code null}
 * (not yet explicitly configured), indicating the default enforcement is active.
 */
@Restricted(NoExternalUse.class)
@Extension
public class CspRecommendation extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return Messages.CspRecommendation_DisplayName();
    }

    @Override
    public boolean isActivated() {
        if (ExtensionList.lookupSingleton(CspConfiguration.class).enforce != null) {
            // configuration was already saved
            return false;
        }
        // Only show when ConfigurationHeaderDecider is in control (not in dev mode or overridden by system property)
        return CspHeaderDecider.getCurrentDecider()
                .filter(d -> d instanceof CspConfiguration.ConfigurationHeaderDecider)
                .isPresent();
    }

    @Override
    public boolean isSecurity() {
        return true;
    }

    @POST
    public void doAct(@QueryParameter String setup, @QueryParameter String dismiss) throws IOException {
        if (setup != null) {
            throw HttpResponses.redirectViaContextPath("manage/configureSecurity/#contentSecurityPolicy");
        }
        if (dismiss != null) {
            disable(true);
        }
        throw HttpResponses.redirectViaContextPath("manage/");
    }
}
