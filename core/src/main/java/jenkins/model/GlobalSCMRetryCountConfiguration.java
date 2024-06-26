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

package jenkins.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import java.io.IOException;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Configures global SCM retry count default.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = 395) @Symbol("scmRetryCount")
public class GlobalSCMRetryCountConfiguration extends GlobalConfiguration {
    public int getScmCheckoutRetryCount() {
        return Jenkins.get().getScmCheckoutRetryCount();
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        try {
            // for compatibility reasons, this value is stored in Jenkins
            Jenkins.get().setScmCheckoutRetryCount(json.getInt("scmCheckoutRetryCount"));
            return true;
        } catch (IOException e) {
            throw new FormException(e, "quietPeriod");
        } catch (JSONException e) {
            throw new FormException(e.getMessage(), "quietPeriod");
        }
    }

    @NonNull
    @Override
    public Permission getRequiredGlobalConfigPagePermission() {
        return Jenkins.MANAGE;
    }
}
