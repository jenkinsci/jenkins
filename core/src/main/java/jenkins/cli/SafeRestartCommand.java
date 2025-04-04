/*
 * The MIT License
 *
 * Copyright (c) 2023, Jan Meiswinkel
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

package jenkins.cli;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.cli.Messages;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.args4j.Option;

/**
 * Safe Restart Jenkins - do not accept any new jobs and try to pause existing.
 *
 * @since 2.414
 */
@Extension
@Restricted(NoExternalUse.class)
public class SafeRestartCommand extends CLICommand {
    private static final Logger LOGGER = Logger.getLogger(SafeRestartCommand.class.getName());

    @Option(name = "-message", usage = "Message for safe restart that will be visible to users")
    public String message = null;

    @Override
    public String getShortDescription() {
        return Messages.SafeRestartCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {
        Jenkins.get().doSafeRestart(null, message);
        return 0;
    }
}
