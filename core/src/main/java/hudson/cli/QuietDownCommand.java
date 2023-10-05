/*
 * The MIT License
 *
 * Copyright (c) 2016 Red Hat, Inc.
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

package hudson.cli;

import hudson.Extension;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Option;

/**
 * Quiet down Jenkins - preparation for a restart
 *
 * @author pjanouse
 * @since 2.14
 */
@Extension
public class QuietDownCommand extends CLICommand {

    private static final Logger LOGGER = Logger.getLogger(QuietDownCommand.class.getName());

    @Option(name = "-block", usage = "Block until the system really quiets down and no builds are running")
    public boolean block = false;

    @Option(name = "-timeout", usage = "If non-zero, only block up to the specified number of milliseconds")
    public int timeout = 0;

    @Option(name = "-reason", usage = "Reason for quiet down that will be visible to users")
    public String reason = null;

    @Override
    public String getShortDescription() {
        return Messages.QuietDownCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {
        Jenkins.get().doQuietDown(block, timeout, reason);
        return 0;
    }
}
