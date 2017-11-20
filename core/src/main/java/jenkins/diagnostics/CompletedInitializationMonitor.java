/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package jenkins.diagnostics;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.model.AdministrativeMonitor;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Performs monitoring of {@link Jenkins} {@link InitMilestone} status.
 *
 * @author Oleg Nenashev
 * @since 2.21
 */
@Restricted(NoExternalUse.class)
@Extension @Symbol("completedInitialization")
public class CompletedInitializationMonitor extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return Messages.CompletedInitializationMonitor_DisplayName();
    }

    @Override
    public boolean isActivated() {
        final Jenkins instance = Jenkins.getInstance();
        // Safe to check in such way, because monitors are being checked in UI only.
        // So Jenkins class construction and initialization must be always finished by the call of this extension.
        return instance.getInitLevel() != InitMilestone.COMPLETED;
    }
}
