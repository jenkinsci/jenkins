/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.model;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.Extension;

/**
 * Parameter whose value is a string, but is hidden from the UI.
 * Useful for passwords, even though its protection is still somewhat limited.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.319
 */
public class PasswordParameterDefinition extends StringParameterDefinition {
    @DataBoundConstructor
    public PasswordParameterDefinition(String name, String defaultValue, String description) {
        super(name, defaultValue, description);
    }

    @Extension
    public final static class ParameterDescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.PasswordParameterDefinition_DisplayName();
        }
    }

    @Override
    public PasswordParameterValue createValue(StaplerRequest req, JSONObject jo) {
        PasswordParameterValue value = req.bindJSON(PasswordParameterValue.class, jo);
        value.setDescription(getDescription());
        return value;
    }
}
