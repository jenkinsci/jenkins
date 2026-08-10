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
import hudson.Util;
import java.util.Objects;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Parameter whose value is a string value.
 */
public class StringParameterDefinition extends SimpleParameterDefinition {

    private String defaultValue;
    private boolean trim;

    /**
     * @since 2.281
     */
    @DataBoundConstructor
    public StringParameterDefinition(@NonNull String name) {
        super(name);
    }

    public StringParameterDefinition(@NonNull String name, @CheckForNull String defaultValue, @CheckForNull String description, boolean trim) {
        this(name);
        setDefaultValue(defaultValue);
        setDescription(description);
        setTrim(trim);
    }

    public StringParameterDefinition(@NonNull String name, @CheckForNull String defaultValue, @CheckForNull String description) {
        this(name);
        setDefaultValue(defaultValue);
        setDescription(description);
    }

    public StringParameterDefinition(@NonNull String name, @CheckForNull String defaultValue) {
        this(name);
        setDefaultValue(defaultValue);
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof StringParameterValue value) {
            return new StringParameterDefinition(getName(), value.value, getDescription());
        } else {
            return this;
        }
    }

    @NonNull
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     *
     * @return original or trimmed defaultValue (depending on trim)
     */
    @Restricted(DoNotUse.class) // Jelly
    public String getDefaultValue4Build() {
        if (isTrim()) {
            return Util.fixNull(defaultValue).trim();
        }
        return defaultValue;
    }

    @DataBoundSetter
    public void setDefaultValue(@CheckForNull String defaultValue) {
        this.defaultValue = Util.fixEmpty(defaultValue);
    }

    /**
     *
     * @return trim - {@code true}, if trim options has been selected, else return {@code false}.
     *      Trimming will happen when creating {@link StringParameterValue}s,
     *      the value in the config will not be changed.
     * @since 2.90
     */
    public boolean isTrim() {
        return trim;
    }

    /**
     * @since 2.281
     */
    @DataBoundSetter
    public void setTrim(boolean trim) {
        this.trim = trim;
    }

    @Override
    public StringParameterValue getDefaultParameterValue() {
        StringParameterValue value = new StringParameterValue(getName(), defaultValue, getDescription());
        if (isTrim()) {
            value.doTrim();
        }
        return value;
    }

    @Extension @Symbol({"string", "stringParam"})
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.StringParameterDefinition_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/string.html";
        }
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);
        if (isTrim()) {
            value.doTrim();
        }
        value.setDescription(getDescription());
        return value;
    }

    @Override
    public ParameterValue createValue(String str) {
        StringParameterValue value = new StringParameterValue(getName(), str, getDescription());
        if (isTrim()) {
            value.doTrim();
        }
        return value;
    }

    @Override
    public int hashCode() {
        if (StringParameterDefinition.class != getClass()) {
            return super.hashCode();
        }
        return Objects.hash(getName(), getDescription(), defaultValue, trim);
    }

    @Override
    @SuppressFBWarnings(value = "EQ_GETCLASS_AND_CLASS_CONSTANT", justification = "ParameterDefinitionTest tests that subclasses are not equal to their parent classes, so the behavior appears to be intentional")
    public boolean equals(Object obj) {
        if (StringParameterDefinition.class != getClass())
            return super.equals(obj);
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        StringParameterDefinition other = (StringParameterDefinition) obj;
        if (!Objects.equals(getName(), other.getName()))
            return false;
        if (!Objects.equals(getDescription(), other.getDescription()))
            return false;
        if (!Objects.equals(defaultValue, other.defaultValue))
            return false;
        return trim == other.trim;
    }
}
