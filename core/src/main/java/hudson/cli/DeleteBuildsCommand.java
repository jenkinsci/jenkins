/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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
import hudson.model.AbstractBuild;
import hudson.model.Run;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Deletes builds records in a bulk.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class DeleteBuildsCommand extends AbstractBuildRangeCommand {
    @Override
    public String getShortDescription() {
        return Messages.DeleteBuildsCommand_ShortDescription();
    }

    @Override
    protected void printUsageSummary(PrintStream stderr) {
        stderr.println(
            "Delete build records of a specified job, possibly in a bulk. "
        );
    }

    @Override
    protected int act(List<AbstractBuild<?, ?>> builds) throws IOException {
        job.checkPermission(Run.DELETE);

        for (AbstractBuild build : builds)
            build.delete();

        stdout.println("Deleted "+builds.size()+" builds");

        return 0;
    }

}
