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
 * {@link ParameterValue} created from {@link StringParameterDefinition}.
 */
public class StringParameterValue extends ParameterValue {
    @Exported(visibility=4)
    public final String value;

    @DataBoundConstructor
    public StringParameterValue(String name, String value) {
        this(name, value, null);
    }

    public StringParameterValue(String name, String value, String description) {
        super(name, description);
        this.value = value;
    }

    /**
     * Exposes the name/value as an environment variable.
     */
    @Override
    public void buildEnvironment(Run<?,?> build, EnvVars env) {
        env.put(name,value);
        env.put(name.toUpperCase(Locale.ENGLISH),value); // backward compatibility pre 1.345
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return new VariableResolver<String>() {
            public String resolve(String name) {
                return StringParameterValue.this.name.equals(name) ? value : null;
            }
        };
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		StringParameterValue other = (StringParameterValue) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

    @Override
    public String toString() {
    	return "(StringParameterValue) " + getName() + "='" + value + "'";
    }

    @Override public String getShortDescription() {
        return name + '=' + value;
    }

}
