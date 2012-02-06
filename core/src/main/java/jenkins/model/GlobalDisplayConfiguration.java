/*
 * The MIT License
 *
 * Copyright  (c) 2012 Thomas Deruyter.
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
 * Show choice of get job display at configuration level
 * 
 * @author Thomas Deruyter
 */

@Extension(ordinal=100)
public class GlobalDisplayConfiguration extends GlobalConfiguration {

    public Integer getShortDisplayNameLength() {
        return Jenkins.getInstance().getShortDisplayNameLength();
    }
    
    public Boolean isUseShortDisplayName() {
        return Jenkins.getInstance().isUseShortDisplayName();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        if (json.has("useShortDisplayName")) {
            JSONObject displayname = json.getJSONObject("useShortDisplayName");
            try {
                Jenkins.getInstance().setShortDisplayNameLength(displayname.getInt("shortDisplayNameLength"));
            } catch (IOException e) {
                throw new FormException(e, "shortDisplayNameLength");
            }
        } else {
            try {
                Jenkins.getInstance().setShortDisplayNameLength(-1);
            } catch (IOException e) {
                throw new FormException(e, "shortDisplayNameLength");
            }
        }
        return true;
    }
}