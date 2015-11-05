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

import hudson.Extension;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Configures the system-default quiet period.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=400)
public class GlobalQuietPeriodConfiguration extends GlobalConfiguration {
    public int getQuietPeriod() {
        return Jenkins.getInstance().getQuietPeriod();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        int i=0;
        try {
            i = Integer.parseInt(json.getString("quietPeriod"));
        } catch (NumberFormatException e) {
            // fall through
        }
        try {
            // for compatibility reasons, this value is stored in Jenkins
            Jenkins.getInstance().setQuietPeriod(i);
            return true;
        } catch (IOException e) {
            throw new FormException(e,"quietPeriod");
        }
    }
}
