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
import hudson.model.Item;

import hudson.model.Items;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reloads job from the disk.
 * @author pjanouse
 * @since 1.633
 */
@Extension
public class ReloadJobCommand extends CLICommand {

    @Argument(usage="Name of the job(s) to reload", required=true, multiValued=true)
    private List<String> jobs;

    private static final Logger LOGGER = Logger.getLogger(ReloadJobCommand.class.getName());

    @Override
    public String getShortDescription() {

        return Messages.ReloadJobCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {

        boolean errorOccurred = false;
        final Jenkins jenkins = Jenkins.get();

        final HashSet<String> hs = new HashSet<>(jobs);

        for (String job_s: hs) {
            AbstractItem job = null;

            try {
                Item item = jenkins.getItemByFullName(job_s);
                if (item instanceof AbstractItem) {
                    job = (AbstractItem) item;
                } else if (item != null) {
                    LOGGER.log(Level.WARNING, "Unsupported item type: {0}", item.getClass().getName());
                }

                if(job == null) {
                    AbstractItem project = Items.findNearest(AbstractItem.class, job_s, jenkins);
                    throw new IllegalArgumentException(project == null ?
                        "No such item \u2018" + job_s + "\u2019 exists." :
                        String.format("No such item \u2018%s\u2019 exists. Perhaps you meant \u2018%s\u2019?",
                                job_s, project.getFullName()));
                }

                job.checkPermission(AbstractItem.CONFIGURE);
                job.doReload();
            } catch (Exception e) {
                if(hs.size() == 1) {
                    throw e;
                }

                final String errorMsg = job_s + ": " + e.getMessage();
                stderr.println(errorMsg);
                errorOccurred = true;
                continue;
            }
        }

        if (errorOccurred) {
            throw new AbortException(CLI_LISTPARAM_SUMMARY_ERROR_TEXT);
        }
        return 0;
    }
}
