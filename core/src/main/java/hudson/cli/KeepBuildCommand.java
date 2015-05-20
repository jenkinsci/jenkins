/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
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

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Run;

/**
 * @author ogondza
 * @since TODO
 */
@Extension
public class KeepBuildCommand extends CLICommand {

    @Argument(metaVar="JOB", usage="Name of the job", required=true, index=0)
    public transient AbstractProject<?, ?> job;

    @Argument(metaVar="BUILD#", usage="Number of the build", required=true, index=1)
    public int number;

    @Override
    public String getShortDescription() {
        return Messages.KeepBuildCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception{
        Run<?, ?> build = job.getBuildByNumber(number);
        if (build == null) throw new CmdLineException(
                null, "No such build '#" + number + "' exists"
        );

        build.keepLog();
        return 0;
    }
}
