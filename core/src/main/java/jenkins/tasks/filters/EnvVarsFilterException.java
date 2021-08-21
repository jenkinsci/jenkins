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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Exception that occurs during the environment filtering process, with helper to track the source.
 *
 * @since 2.246
 */
@Restricted(Beta.class)
public class EnvVarsFilterException extends AbortException {
    private EnvVarsFilterRule rule;
    private String variableName;

    public EnvVarsFilterException(String message) {
        super(message);
    }

    public @NonNull
    EnvVarsFilterException withRule(@NonNull EnvVarsFilterRule rule) {
        this.rule = rule;
        return this;
    }

    public @NonNull EnvVarsFilterException withVariable(@NonNull String variableName) {
        this.variableName = variableName;
        return this;
    }

    public @CheckForNull
    EnvVarsFilterRule getRule() {
        return rule;
    }

    @Override
    public @NonNull String getMessage() {
        String message = super.getMessage();
        if (variableName != null) {
            message += " due to variable '" + variableName + "'";
        }
        if (rule != null) {
            if (rule instanceof EnvVarsFilterGlobalRule) {
                message += " detected by the global rule " + rule.getDisplayName();
            } else {
                message += " detected by " + rule.getDisplayName();
            }
        }
        return message;
    }
}
