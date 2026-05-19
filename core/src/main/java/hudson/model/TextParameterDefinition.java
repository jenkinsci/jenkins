/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Luca Domenico Milanesio, Seiji Sogabe, Tom Huybrechts
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
import java.util.Objects;
import jenkins.model.EnvironmentVariableParameterNameFormValidation;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * {@link StringParameterDefinition} that uses textarea, instead of text box.
 */
public class TextParameterDefinition extends StringParameterDefinition {

    /**
     * @since 2.281
     */
    @DataBoundConstructor
    public TextParameterDefinition(@NonNull String name) {
        super(name);
    }

    public TextParameterDefinition(@NonNull String name, @CheckForNull String defaultValue, @CheckForNull String description) {
        this(name);
        setDefaultValue(defaultValue);
        setDescription(description);
    }

    @Extension @Symbol({"text", "textParam"})
    public static class DescriptorImpl extends ParameterDescriptor implements EnvironmentVariableParameterNameFormValidation {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.TextParameterDefinition_DisplayName();
        }
    }

    @Override
    public StringParameterValue getDefaultParameterValue() {
        return new TextParameterValue(getName(), getDefaultValue(), getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        TextParameterValue value = req.bindJSON(TextParameterValue.class, jo);
        value.setDescription(getDescription());
        return value;
    }

    @Override
    public ParameterValue createValue(String value) {
        return new TextParameterValue(getName(), value, getDescription());
    }

    @Override
    public int hashCode() {
        if (TextParameterDefinition.class != getClass()) {
            return super.hashCode();
        }
        return Objects.hash(getName(), getDescription(), getDefaultValue(), isTrim());
    }

    @Override
    @SuppressFBWarnings(value = "EQ_GETCLASS_AND_CLASS_CONSTANT", justification = "ParameterDefinitionTest tests that subclasses are not equal to their parent classes, so the behavior appears to be intentional")
    public boolean equals(Object obj) {
        if (TextParameterDefinition.class != getClass())
            return super.equals(obj);
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TextParameterDefinition other = (TextParameterDefinition) obj;
        if (!Objects.equals(getName(), other.getName()))
            return false;
        if (!Objects.equals(getDescription(), other.getDescription()))
            return false;
        if (!Objects.equals(getDefaultValue(), other.getDefaultValue()))
            return false;
        return isTrim() == other.isTrim();
    }
}
