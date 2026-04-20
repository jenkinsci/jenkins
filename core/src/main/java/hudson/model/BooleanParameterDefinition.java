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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import java.util.Objects;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * {@link ParameterDefinition} that is either 'true' or 'false'.
 *
 * @author huybrechts
 */
public class BooleanParameterDefinition extends SimpleParameterDefinition {
    private boolean defaultValue;

    /**
     * @since 2.281
     */
    @DataBoundConstructor
    public BooleanParameterDefinition(@NonNull String name) {
        super(name);
    }

    public BooleanParameterDefinition(@NonNull String name, boolean defaultValue, @CheckForNull String description) {
        this(name);
        setDefaultValue(defaultValue);
        setDescription(description);
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof BooleanParameterValue value) {
            return new BooleanParameterDefinition(getName(), value.value, getDescription());
        } else {
            return this;
        }
    }

    public boolean isDefaultValue() {
        return defaultValue;
    }

    /**
     * @since 2.281
     */
    @DataBoundSetter
    public void setDefaultValue(boolean defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        BooleanParameterValue value = req.bindJSON(BooleanParameterValue.class, jo);
        value.setDescription(getDescription());
        return value;
    }

    @Override
    public ParameterValue createValue(String value) {
        return new BooleanParameterValue(getName(), Boolean.parseBoolean(value), getDescription());
    }

    @Override
    public BooleanParameterValue getDefaultParameterValue() {
        return new BooleanParameterValue(getName(), defaultValue, getDescription());
    }

    @Override
    public int hashCode() {
        if (BooleanParameterDefinition.class != getClass()) {
            return super.hashCode();
        }
        return Objects.hash(getName(), getDescription(), defaultValue);
    }

    @Override
    @SuppressFBWarnings(value = "EQ_GETCLASS_AND_CLASS_CONSTANT", justification = "ParameterDefinitionTest tests that subclasses are not equal to their parent classes, so the behavior appears to be intentional")
    public boolean equals(Object obj) {
        if (BooleanParameterDefinition.class != getClass())
            return super.equals(obj);
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BooleanParameterDefinition other = (BooleanParameterDefinition) obj;
        if (!Objects.equals(getName(), other.getName()))
            return false;
        if (!Objects.equals(getDescription(), other.getDescription()))
            return false;
        return defaultValue == other.defaultValue;
    }

    // unlike all the other ParameterDescriptors, using 'booleanParam' as the primary
    // to avoid picking the Java reserved word "boolean" as the primary identifier
    @Extension @Symbol("booleanParam")
    public static class DescriptorImpl extends ParameterDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.BooleanParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/boolean.html";
        }
    }

}
