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
import hudson.util.HudsonIsLoading;
import hudson.util.JenkinsReloadFailed;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.WebApp;

/**
 * Reload everything from the file system.
 *
 * @author pjanouse
 * @since 2.4
 */
@Extension
public class ReloadConfigurationCommand extends CLICommand {

    @Override
    public String getShortDescription() {
        return Messages.ReloadConfigurationCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {
        Jenkins j = Jenkins.get();
        // Or perhaps simpler to inline the thread body of doReload?
        j.doReload();
        Object app;
        while ((app = WebApp.get(j.getServletContext()).getApp()) instanceof HudsonIsLoading) {
            Thread.sleep(100);
        }
        if (app instanceof Jenkins) {
            return 0;
        } else if (app instanceof JenkinsReloadFailed) {
            Throwable t = ((JenkinsReloadFailed) app).cause;
            if (t instanceof Exception) {
                throw (Exception) t;
            } else {
                throw new RuntimeException(t);
            }
        } else {
            stderr.println("Unexpected status " + app);
            return 1; // could throw JenkinsReloadFailed.cause if it were not deprecated
        }
    }

}
