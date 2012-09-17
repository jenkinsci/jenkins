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

import jenkins.model.Jenkins;
import hudson.model.TopLevelItem;
import hudson.Extension;
import hudson.model.Item;
import org.kohsuke.args4j.Argument;


/**
 * Copies a job from CLI.
 * 
 * @author Kohsuke Kawaguchi
 */
@Extension
public class CopyJobCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.CopyJobCommand_ShortDescription();
    }

    @Argument(metaVar="SRC",usage="Name of the job to copy",required=true)
    public TopLevelItem src;

    @Argument(metaVar="DST",usage="Name of the new job to be created.",index=1,required=true)
    public String dst;

    protected int run() throws Exception {
        Jenkins h = Jenkins.getInstance();
        h.checkPermission(Item.CREATE);

        if (h.getItem(dst)!=null) {
            stderr.println("Job '"+dst+"' already exists");
            return -1;
        }
        
        h.copy(src,dst);
        return 0;
    }
}

