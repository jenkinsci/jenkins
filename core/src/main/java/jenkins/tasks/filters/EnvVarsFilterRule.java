/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
package jenkins.tasks.filters;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import java.io.Serializable;

/**
 * The order of execution of the rules is determined by first their type (local before global)
 * and then, by default, their {@link Extension#ordinal()}, higher ordinal first, but configuration can customize the order.
 */
@Restricted(Beta.class)
public interface EnvVarsFilterRule extends Serializable {
    /**
     * In case the filter detects something that must stop the build, it must throw a {@link EnvVarsFilterException}.
     * This method may be executed on agents through a remoting channel.
     */
    void filter(@NonNull EnvVars envVars, @NonNull EnvVarsFilterRuleContext context) throws EnvVarsFilterException;

    default String getDisplayName() {
        if (this instanceof Describable<?>) {
            final Descriptor<?> descriptor = ((Describable<?>) this).getDescriptor();
            if (descriptor != null) {
                return descriptor.getDisplayName();
            }
        }
        return this.getClass().getSimpleName();
    }
}
