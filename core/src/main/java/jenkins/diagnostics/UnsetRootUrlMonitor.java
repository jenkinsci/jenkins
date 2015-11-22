/*
 * The MIT License
 *
 * Copyright 2015 Antonio Muñiz.
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

package jenkins.diagnostics;

import java.io.IOException;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import jenkins.model.JenkinsLocationConfiguration;

/**
 * Jenkins root URL is required for a lot of operations in both core and plugins.
 * There is a default behavior (infer the URL from the request object), but innacurate in some scenarios.
 */
@Extension
public class UnsetRootUrlMonitor extends AdministrativeMonitor {

    @Override
    public boolean isActivated() {
        return JenkinsLocationConfiguration.get().getUrl() == null;
    }

    public HttpResponse doAct(@QueryParameter String no) throws IOException {
        if(no != null) { // Dismiss
            disable(true);
            return HttpResponses.redirectViaContextPath("/manage");
        } else { // Configure
            return HttpResponses.redirectViaContextPath("/configure");
        }
    }

}
