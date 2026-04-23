/*
 * The MIT License
 *
 * Copyright (c) 2026, Jan Faracik
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

package jenkins.model.job;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewJobPageUserExperimentalFlag;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * App bar action that links to the workspace browser for an {@link AbstractProject}.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public final class WorkspaceAction implements Action {

    @Override
    public String getDisplayName() {
        return Messages.WorkspaceAction_Title();
    }

    @Override
    public String getIconFileName() {
        return "symbol-folder";
    }

    @Override
    public String getUrlName() {
        return "ws";
    }

    @Extension
    @Restricted(Beta.class)
    public static final class Factory extends TransientActionFactory<AbstractProject> {

        @Override
        public Class<AbstractProject> type() {
            return AbstractProject.class;
        }

        @Override
        public Collection<? extends Action> createFor(AbstractProject target) {
            // This condition can be removed when the flag has been removed
            if (!new NewJobPageUserExperimentalFlag().getFlagValue()) {
                return Set.of();
            }

            if (!target.hasPermission(AbstractProject.WORKSPACE)) {
                return Set.of();
            }

            return Set.of(new WorkspaceAction());
        }
    }
}
