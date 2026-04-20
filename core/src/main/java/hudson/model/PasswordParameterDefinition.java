/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Romain Seguy
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.util.Secret;
import java.util.Objects;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Parameter whose value is a {@link Secret} and is hidden from the UI.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.319
 */
public class PasswordParameterDefinition extends SimpleParameterDefinition {

    @Restricted(NoExternalUse.class)
    public static final String DEFAULT_VALUE = "<DEFAULT>";

    private Secret defaultValue;

    @Deprecated
    public PasswordParameterDefinition(@NonNull String name, @CheckForNull String defaultValue, @CheckForNull String description) {
        super(name, description);
        this.defaultValue = Secret.fromString(defaultValue);
    }

    // TODO consider switching @DataBoundConstructor to a PasswordParameterDefinition(String) overload
    @DataBoundConstructor
    public PasswordParameterDefinition(@NonNull String name, @CheckForNull Secret defaultValueAsSecret, @CheckForNull String description) {
        super(name, description);
        this.defaultValue = defaultValueAsSecret;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof PasswordParameterValue value) {
            return new PasswordParameterDefinition(getName(), Secret.toString(value.getValue()), getDescription());
        } else {
            return this;
        }
    }

    @Override
    public ParameterValue createValue(String value) {
        return new PasswordParameterValue(getName(), value, getDescription());
    }

    @Override
    public PasswordParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        PasswordParameterValue value = req.bindJSON(PasswordParameterValue.class, jo);
        if (value.getValue().getPlainText().equals(DEFAULT_VALUE)) {
            value = new PasswordParameterValue(getName(), getDefaultValue());
        }
        value.setDescription(getDescription());
        return value;
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        return new PasswordParameterValue(getName(), getDefaultValue(), getDescription());
    }

    @NonNull
    public String getDefaultValue() {
        return Secret.toString(defaultValue);
    }

    @Restricted(DoNotUse.class) // used from Jelly
    public Secret getDefaultValueAsSecret() {
        return defaultValue;
    }

    // kept for backward compatibility
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = Secret.fromString(defaultValue);
    }

    @Override
    public int hashCode() {
        if (PasswordParameterDefinition.class != getClass()) {
            return super.hashCode();
        }
        return Objects.hash(getName(), getDescription(), defaultValue);
    }

    @Override
    @SuppressFBWarnings(value = "EQ_GETCLASS_AND_CLASS_CONSTANT", justification = "ParameterDefinitionTest tests that subclasses are not equal to their parent classes, so the behavior appears to be intentional")
    public boolean equals(Object obj) {
        if (PasswordParameterDefinition.class != getClass())
            return super.equals(obj);
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PasswordParameterDefinition other = (PasswordParameterDefinition) obj;
        if (!Objects.equals(getName(), other.getName()))
            return false;
        if (!Objects.equals(getDescription(), other.getDescription()))
            return false;
        return Objects.equals(defaultValue, other.defaultValue);
    }

    @Extension @Symbol("password")
    public static final class ParameterDescriptorImpl extends ParameterDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.PasswordParameterDefinition_DisplayName();
        }
    }
}
