/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package jenkins.security.apitoken;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.util.HttpResponses;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;

/**
 * Monitor that the API Token cannot be created for a user without existing legacy token
 */
@Extension
@Symbol("apiTokenNewLegacyWithoutExisting")
@Restricted(NoExternalUse.class)
public class ApiTokenPropertyEnabledNewLegacyAdministrativeMonitor extends AdministrativeMonitor {
    @Override
    public String getDisplayName() {
        return Messages.ApiTokenPropertyEnabledNewLegacyAdministrativeMonitor_displayName();
    }
    
    @Override
    public boolean isActivated() {
        return ApiTokenPropertyConfiguration.get().isCreationOfLegacyTokenEnabled();
    }

    @Override
    public boolean isSecurity() {
        return true;
    }

    @RequirePOST
    public HttpResponse doAct(@QueryParameter String no) throws IOException {
        if (no == null) {
            ApiTokenPropertyConfiguration.get().setCreationOfLegacyTokenEnabled(false);
        } else {
            disable(true);
        }
        return HttpResponses.redirectViaContextPath("manage");
    }
}
