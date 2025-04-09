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

package jenkins.health;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.json.JsonHttpResponse;

/**
 * Provides a health check action for Jenkins.
 */
@Extension
@Restricted(DoNotUse.class)
public final class HealthCheckAction extends InvisibleAction implements UnprotectedRootAction {

    @Override
    public String getUrlName() {
        return "healthCheck";
    }

    public HttpResponse doIndex() {
        boolean success = true;
        var checks = new JSONArray();
        for (var healthCheck : ExtensionList.lookup(HealthCheck.class)) {
            var check = healthCheck.check();
            var name = healthCheck.getName();
            success &= check;
            checks.add(new JSONObject().element("name", name).element("result", check));
        }
        return new JsonHttpResponse(new JSONObject().element("status", success).element("data", checks), success ? 200 : 503);
    }
}
