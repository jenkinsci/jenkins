/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Romain Seguy, Yahoo! Inc.
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
import hudson.util.Secret;
import hudson.util.VariableResolver;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Locale;

/**
 * @author Kohsuke Kawaguchi
 */
public class PasswordParameterValue extends ParameterValue {

    private final Secret value;

    // kept for backward compatibility
    public PasswordParameterValue(String name, String value) {
        this(name, value, null);
    }

    @DataBoundConstructor
    public PasswordParameterValue(String name, String value, String description) {
        super(name, description);
        this.value = Secret.fromString(value);
    }

    @Override
    public void buildEnvironment(Run<?,?> build, EnvVars env) {
        String v = Secret.toString(value);
        env.put(name, v);
        env.put(name.toUpperCase(Locale.ENGLISH),v); // backward compatibility pre 1.345
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return new VariableResolver<String>() {
            public String resolve(String name) {
                return PasswordParameterValue.this.name.equals(name) ? Secret.toString(value) : null;
            }
        };
    }

    @Override
    public boolean isSensitive() {
        return true;
    }
    
    public Secret getValue() {
        return value;
    }

    @Override public String getShortDescription() {
        return name + "=****";
    }

}
