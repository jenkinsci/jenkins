/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jean-Baptiste Quenot, Martin Eigenbrodt
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
package hudson.triggers;

import static hudson.Util.fixNull;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.scheduler.CronTabList;
import hudson.util.FormValidation;
import hudson.Extension;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import antlr.ANTLRException;

/**
 * {@link Trigger} that runs a job periodically.
 *
 * @author Kohsuke Kawaguchi
 */
public class TimerTrigger extends Trigger<BuildableItem> {

    @DataBoundConstructor
    public TimerTrigger(String timer_spec) throws ANTLRException {
        super(timer_spec);
    }

    public void run() {
        job.scheduleBuild(0, new TimerTriggerCause());
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        public boolean isApplicable(Item item) {
            return item instanceof BuildableItem;
        }

        public String getDisplayName() {
            return Messages.TimerTrigger_DisplayName();
        }

        public String getHelpFile() {
            return "/help/project-config/timer.html";
        }

        /**
         * Performs syntax check.
         */
        public FormValidation doCheck(@QueryParameter String value) {
            try {
                String msg = CronTabList.create(fixNull(value)).checkSanity();
                if(msg!=null)   return FormValidation.warning(msg);
                return FormValidation.ok();
            } catch (ANTLRException e) {
                return FormValidation.error(e.getMessage());
            }
        }
    }
    
    public static class TimerTriggerCause extends Cause {
        @Override
        public String getShortDescription() {
            return Messages.TimerTrigger_TimerTriggerCause_ShortDescription();
        }
    }
}
