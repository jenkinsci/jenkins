/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Luca Domenico Milanesio, Tom Huybrechts
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

import hudson.EnvVars;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.util.Locale;

import hudson.util.VariableResolver;

/**
 * {@link ParameterValue} created from {@link BooleanParameterDefinition}.
 */
public class BooleanParameterValue extends ParameterValue {
    @Exported(visibility=4)
    public final boolean value;

    @DataBoundConstructor
    public BooleanParameterValue(String name, boolean value) {
        this(name, value, null);
    }

    public BooleanParameterValue(String name, boolean value, String description) {
        super(name, description);
        this.value = value;
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    /**
     * Exposes the name/value as an environment variable.
     */
    @Override
    public void buildEnvironment(Run<?,?> build, EnvVars env) {
        env.put(name,Boolean.toString(value));
        env.put(name.toUpperCase(Locale.ENGLISH),Boolean.toString(value)); // backward compatibility pre 1.345
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return new VariableResolver<String>() {
            public String resolve(String name) {
                return BooleanParameterValue.this.name.equals(name) ? Boolean.toString(value) : null;
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BooleanParameterValue that = (BooleanParameterValue) o;

        if (value != that.value) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (value ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
    	return "(BooleanParameterValue) " + getName() + "='" + value + "'";
    }

    @Override public String getShortDescription() {
        return name + '=' + value;
    }

}
