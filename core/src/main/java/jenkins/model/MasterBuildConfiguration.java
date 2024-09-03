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
import hudson.model.Node.Mode;
import java.io.IOException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Adds the configuration regarding building on the built-in node.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal = 500) @Symbol({"builtInNode", "masterBuild"})
public class MasterBuildConfiguration extends GlobalConfiguration {
    public int getNumExecutors() {
        return Jenkins.get().getNumExecutors();
    }

    public String getLabelString() {
        return Jenkins.get().getLabelString();
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        Jenkins j = Jenkins.get();
        try {
            // for compatibility reasons, this value is stored in Jenkins
            String num = json.getString("numExecutors");
            if (!num.matches("\\d+")) {
                throw new FormException(Messages.Hudson_Computer_IncorrectNumberOfExecutors(), "numExecutors");
            }

            j.setNumExecutors(json.getInt("numExecutors"));
            if (req.hasParameter("builtin.mode"))
                j.setMode(Mode.valueOf(req.getParameter("builtin.mode")));
            else
                j.setMode(Mode.NORMAL);

            j.setLabelString(json.optString("labelString", ""));

            return true;
        } catch (IOException e) {
            throw new FormException(e, "numExecutors");
        }
    }
}
