/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.model.AperiodicWork;
import hudson.model.PeriodicWork;
import hudson.security.ACL;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

/**
 * Wrapper so that a fatal error in {@link TimerTask} will not terminate the timer.
 *
 * <p>
 * {@link Timer#get} is a shared timer instance that can be used inside Jenkins to schedule recurring work.
 * But the usual usage is automatic via {@link PeriodicWork} or {@link AperiodicWork}.
 * @author Kohsuke Kawaguchi
 * @since 1.124
 */
public abstract class SafeTimerTask extends TimerTask {
    public final void run() {
        // background activity gets system credential,
        // just like executors get it.
        SecurityContext oldContext = ACL.impersonate(ACL.SYSTEM);
        try {
            doRun();
        } catch(Throwable t) {
            LOGGER.log(Level.SEVERE, "Timer task "+this+" failed",t);
        } finally {
            SecurityContextHolder.setContext(oldContext);
        }
    }

    protected abstract void doRun() throws Exception;

    private static final Logger LOGGER = Logger.getLogger(SafeTimerTask.class.getName());
}
