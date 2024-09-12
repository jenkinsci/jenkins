/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package jenkins.security.stapler;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Function;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.event.FilteredDispatchTriggerListener;
import org.kohsuke.stapler.event.FilteredDoActionTriggerListener;
import org.kohsuke.stapler.event.FilteredFieldTriggerListener;
import org.kohsuke.stapler.event.FilteredGetterTriggerListener;
import org.kohsuke.stapler.lang.FieldRef;

/**
 * Log a warning message when a "getter" or "doAction" function or fragment view that was filtered out by SECURITY-400 new rules
 */
@Restricted(NoExternalUse.class)
public class StaplerFilteredActionListener implements FilteredDoActionTriggerListener, FilteredGetterTriggerListener, FilteredFieldTriggerListener, FilteredDispatchTriggerListener {
    private static final Logger LOGGER = Logger.getLogger(StaplerFilteredActionListener.class.getName());

    private static final String LOG_MESSAGE = "New Stapler routing rules result in the URL \"{0}\" no longer being allowed. " +
            "If you consider it safe to use, add the following to the whitelist: \"{1}\". " +
            "Learn more: https://www.jenkins.io/redirect/stapler-routing";

    @Override
    public boolean onDoActionTrigger(Function f, StaplerRequest2 req, StaplerResponse2 rsp, Object node) {
        LOGGER.log(Level.FINER, LOG_MESSAGE, new Object[]{
                req.getPathInfo(),
                f.getSignature(),
        });
        return false;
    }

    @Override
    public boolean onGetterTrigger(Function f, StaplerRequest2 req, StaplerResponse2 rsp, Object node, String expression) {
        LOGGER.log(Level.FINER, LOG_MESSAGE, new Object[]{
                req.getPathInfo(),
                f.getSignature(),
        });
        return false;
    }

    @Override
    public boolean onFieldTrigger(FieldRef f, StaplerRequest2 req, StaplerResponse2 staplerResponse, Object node, String expression) {
        LOGGER.log(Level.FINER, LOG_MESSAGE, new Object[]{
                req.getPathInfo(),
                f.getSignature(),
        });
        return false;
    }

    @Override
    public boolean onDispatchTrigger(StaplerRequest2 req, StaplerResponse2 rsp, Object node, String viewName) {
        LOGGER.finer(() -> "New Stapler dispatch rules result in the URL \"" + req.getPathInfo() + "\" no longer being allowed. " +
                "If you consider it safe to use, add the following to the whitelist: \"" + node.getClass().getName() + " " + viewName + "\". " +
                "Learn more: https://www.jenkins.io/redirect/stapler-facet-restrictions");
        return false;
    }
}
