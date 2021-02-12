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

import hudson.Extension;
import hudson.Util;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Objects;

/**
 * Parameter whose value is a string value.
 */
public class StringParameterDefinition extends SimpleParameterDefinition {

    private String defaultValue;
    private final boolean trim;

    @DataBoundConstructor
    public StringParameterDefinition(String name, String defaultValue, String description, boolean trim) {
        super(name, description);
        this.defaultValue = defaultValue;
        this.trim = trim;
    }

    public StringParameterDefinition(String name, String defaultValue, String description) {
        this(name, defaultValue, description, false);
    }
    
    public StringParameterDefinition(String name, String defaultValue) {
        this(name, defaultValue, null, false);
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof StringParameterValue) {
            StringParameterValue value = (StringParameterValue) defaultValue;
            return new StringParameterDefinition(getName(), value.value, getDescription());
        } else {
            return this;
        }
    }

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
    
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
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
    
    @Override
    public StringParameterValue getDefaultParameterValue() {
        StringParameterValue value = new StringParameterValue(getName(), defaultValue, getDescription());
        if (isTrim()) {
            value.doTrim();
        }
        return value;
    }

    @Extension @Symbol({"string","stringParam"})
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
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);
        if (isTrim() && value!=null) {
            value.doTrim();
        }
        value.setDescription(getDescription());
        return value;
    }

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
