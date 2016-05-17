/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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

import hudson.AbortException;
import hudson.Extension;
import hudson.model.AbstractItem;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

import java.util.List;
import java.util.HashSet;
import java.util.logging.Logger;

/**
 * @author pjanouse
 * @since TODO
 */
@Extension
public class DeleteJobCommand extends CLICommand {

    @Argument(usage="Name of the job(s) to delete", required=true, multiValued=true)
    private List<String> jobs;

    @Override
    public String getShortDescription() {

        return Messages.DeleteJobCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {

        boolean errorOccurred = false;
        final Jenkins jenkins = Jenkins.getActiveInstance();

        final HashSet<String> hs = new HashSet<String>();
        hs.addAll(jobs);

        for (String job_s: hs) {
            AbstractItem job = null;

            try {
                job = (AbstractItem) jenkins.getItemByFullName(job_s);

                if(job == null) {
                    throw new IllegalArgumentException("No such job '" + job_s + "'");
                }

                job.checkPermission(AbstractItem.DELETE);
                job.delete();
            } catch (Exception e) {
                if(hs.size() == 1) {
                    throw e;
                }

                final String errorMsg = String.format(job_s + ": " + e.getMessage());
                stderr.println(errorMsg);
                errorOccurred = true;
                continue;
            }
        }

        if (errorOccurred) {
            throw new AbortException("Error occured while performing this command, see previous stderr output.");
        }
        return 0;
    }
}
