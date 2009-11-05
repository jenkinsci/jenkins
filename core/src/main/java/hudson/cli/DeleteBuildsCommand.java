/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Fingerprint.RangeSet;
import hudson.Extension;
import org.kohsuke.args4j.Argument;

import java.io.PrintStream;
import java.util.List;

/**
 * Deletes builds records in a bulk.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class DeleteBuildsCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return "Deletes build record(s)";
    }

    @Argument(metaVar="JOB",usage="Name of the job to build",required=true,index=0)
    public AbstractProject<?,?> job;

    @Argument(metaVar="RANGE",usage="Range of the build records to delete. 'N-M', 'N,M', or 'N'",required=true,index=1)
    public String range;

    @Override
    protected void printUsageSummary(PrintStream stderr) {
        stderr.println(
            "Delete build records of a specified job, possibly in a bulk. "
        );
    }

    protected int run() throws Exception {
        RangeSet rs = RangeSet.fromString(range,false);
        List<? extends AbstractBuild> builds = job.getBuilds(rs);

        for (AbstractBuild build : builds)
            build.delete();

        stdout.println("Deleted "+builds.size()+" builds");

        return 0;
    }
}
