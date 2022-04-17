/*
 * The MIT License
 *
 * Copyright 2021 Tim Jacomb.
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

package jenkins.monitor;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.security.Permission;
import java.io.IOException;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import jenkins.util.java.JavaUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
@Restricted(NoExternalUse.class)
@Symbol("javaVersionRecommendation")
public class JavaVersionRecommendationAdminMonitor extends AdministrativeMonitor {

    public JavaVersionRecommendationAdminMonitor() {
        super(JavaVersionRecommendationAdminMonitor.class.getName() + "-2");
    }

    private static Boolean disabled = SystemProperties.getBoolean(JavaVersionRecommendationAdminMonitor.class.getName() + ".disabled", false);

    @Override
    public boolean isActivated() {
        return !disabled && JavaUtils.isRunningWithJava8OrBelow();
    }

    @Override
    public String getDisplayName() {
        return Messages.JavaLevelAdminMonitor_DisplayName();
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @Restricted(DoNotUse.class) // WebOnly
    @RequirePOST
    public HttpResponse doAct(@QueryParameter String no) throws IOException {
        if (no != null) { // dismiss
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            disable(true);
            return HttpResponses.forwardToPreviousPage();
        } else {
            return new HttpRedirect("https://www.jenkins.io/redirect/upgrading-jenkins-java-version-8-to-11");
        }
    }

}
