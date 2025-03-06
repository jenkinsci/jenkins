/*
 * The MIT License
 *
 * Copyright (c) 2025
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

package hudson.cli.listeners;

import hudson.Extension;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

/**
 * Basic default implementation of {@link CliListener} that just logs.
 */
@Extension
public class DefaultCliListener implements CliListener {
    private static final Logger LOGGER = Logger.getLogger(DefaultCliListener.class.getName());

    @Override
    public void onExecution(String correlationId, String command, int argsSize, Authentication auth) {
        LOGGER.log(Level.FINE, "Invoking CLI command {0}, with {1} arguments, as user {2}.", new Object[] {
            command, argsSize, auth != null ? auth.getName() : "<unknown>",
        });
    }

    @Override
    public void onCompleted(String correlationId, String command, int argsSize, Authentication auth, int exitCode) {
        LOGGER.log(
                Level.FINE,
                "Executed CLI command {0}, with {1} arguments, as user {2}, return code {3}",
                new Object[] {command, argsSize, auth != null ? auth.getName() : "<unknown>", exitCode});
    }

    @Override
    public void onError(
            String correlationId, String command, int argsSize, Authentication auth, boolean expected, Throwable t) {
        if (expected) {
            LOGGER.log(
                    Level.FINE,
                    String.format(
                            "Failed call to CLI command %s, with %d arguments, as user %s.",
                            command, argsSize, auth != null ? auth.getName() : "<unknown>"),
                    t);

        } else {
            LOGGER.log(Level.WARNING, "Unexpected exception occurred while performing " + command + " command.", t);
        }
    }

    @Override
    public void onLoginFailed(
            String correlationId, String command, int argsSize, String opaqueId, BadCredentialsException authError) {
        LOGGER.log(Level.INFO, "CLI login attempt failed: " + opaqueId, authError);
    }
}
