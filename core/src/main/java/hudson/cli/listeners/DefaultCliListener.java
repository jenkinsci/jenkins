/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

import hudson.AbortException;
import hudson.Extension;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.CmdLineException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

/**
 * Basic default implementation of {@link CliListener} that just logs.
 */
@Extension
@Restricted(NoExternalUse.class)
public class DefaultCliListener implements CliListener {
    private static final Logger LOGGER = Logger.getLogger(DefaultCliListener.class.getName());

    @Override
    public void onExecution(CliContext context) {
        LOGGER.log(Level.FINE, "Invoking CLI command {0}, with {1} arguments, as user {2}.", new Object[] {
            context.getCommand(), context.getArgs().size(), authName(context.getAuth()),
        });
    }

    @Override
    public void onCompleted(CliContext context, int exitCode) {
        LOGGER.log(
                Level.FINE, "Executed CLI command {0}, with {1} arguments, as user {2}, return code {3}", new Object[] {
                    context.getCommand(), context.getArgs().size(), authName(context.getAuth()), exitCode,
                });
    }

    @Override
    public void onException(CliContext context, Throwable t) {
        if (t instanceof BadCredentialsException) {
            // to the caller (stderr), we can't reveal whether the user didn't exist or the password didn't match.
            // do that to the server log instead
            LOGGER.log(Level.INFO, "CLI login attempt failed: " + context.getCorrelationId(), t);
        } else if (t instanceof CmdLineException
                || t instanceof IllegalArgumentException
                || t instanceof IllegalStateException
                || t instanceof AbortException
                || t instanceof AccessDeniedException) {
            // covered cases on CLICommand#handleException
            LOGGER.log(
                    Level.FINE,
                    String.format(
                            "Failed call to CLI command %s, with %d arguments, as user %s.",
                            context.getCommand(), context.getArgs().size(), authName(context.getAuth())),
                    t);
        } else {
            LOGGER.log(
                    Level.WARNING,
                    "Unexpected exception occurred while performing " + context.getCommand() + " command.",
                    t);
        }
    }

    private static String authName(Authentication auth) {
        return auth != null ? auth.getName() : "<unknown>";
    }
}
