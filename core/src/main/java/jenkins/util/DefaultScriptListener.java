/*
 * The MIT License
 *
 * Copyright (c) 2022 CloudBees, Inc.
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

package jenkins.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.Binding;
import hudson.Extension;
import hudson.model.User;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Basic default implementation of {@link jenkins.util.ScriptListener} that just logs.
 *
 * @since 2.427
 */
@Extension
@Restricted(NoExternalUse.class)
public class DefaultScriptListener implements ScriptListener {
    public static final Logger LOGGER = Logger.getLogger(DefaultScriptListener.class.getName());

    @Override
    public void onScriptExecution(String script, Binding binding, @NonNull Object feature, Object context, @NonNull String correlationId, User user) {
        String userFragment = user == null ? " (no user)" : " by user: '" + user + "'";
        LOGGER.log(Level.FINE, LOGGER.isLoggable(Level.FINEST) ? new Exception() : null,
                () -> "Execution of script: '" + script + "' with binding: '" + stringifyBinding(binding) + "' in feature: '" + feature + "' and context: '" + context + "' with correlation: '" + correlationId + "'" + userFragment);
    }

    @Override
    public void onScriptOutput(String output, @NonNull Object feature, Object context, @NonNull String correlationId, User user) {
        String userFragment = user == null ? " (no user)" : " for user: '" + user + "'";
        LOGGER.log(Level.FINER, LOGGER.isLoggable(Level.FINEST) ? new Exception() : null,
                () -> "Script output: '" + output + "' in feature: '" + feature + "' and context: '" + context + "' with correlation: '" + correlationId + "'" + userFragment);
    }

    private static String stringifyBinding(Binding binding) {
        if (binding == null) {
            return null;
        }
        return InvokerHelper.toString(binding.getVariables());
    }
}
