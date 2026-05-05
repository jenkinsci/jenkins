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

package jenkins.model.menu.action;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewBuildPageUserExperimentalFlag;
import jenkins.model.experimentalflags.NewJobPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.ConfirmationEvent;
import jenkins.model.menu.event.Event;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * A reusable app bar action that deletes an item after user confirmation.
 *
 * @since 2.560
 */
@Restricted(Beta.class)
public final class DeleteAction implements Action {

    private final String pronoun;

    private final String displayName;

    /**
     * Create a delete action.
     *
     * @param pronoun   the pronoun of the item being deleted, used in the button label
     *                  (e.g. {@code "Job"} → <i>Delete Job</i>).
     * @param displayName the display name of the item being deleted, used in the
     *                    confirmation dialog title.
     */
    public DeleteAction(String pronoun, String displayName) {
        this.pronoun = pronoun;
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return Messages.DeleteAction_Delete(pronoun);
    }

    @Override
    public String getIconFileName() {
        return "symbol-trash";
    }

    @Override
    public Group getGroup() {
        return Group.LAST_IN_MENU;
    }

    @Override
    public String getUrlName() {
        return null;
    }

    @Override
    public Event getEvent() {
        return ConfirmationEvent.of(Messages.DeleteAction_DeleteDialog_Title(displayName), "doDelete");
    }

    @Override
    public Semantic getSemantic() {
        return Semantic.DESTRUCTIVE;
    }

    /**
     * Factory that contributes a {@link DeleteAction} to every {@link Job} the current
     * user has {@link Job#DELETE} permission on when the new job page experimental flag
     * is enabled.
     */
    @Extension(ordinal = 80)
    @Restricted(Beta.class)
    public static final class JobFactory extends TransientActionFactory<Job> {

        @Override
        public Class<Job> type() {
            return Job.class;
        }

        @Override
        public Collection<? extends Action> createFor(Job target) {
            // This condition can be removed when the flag has been removed
            if (!new NewJobPageUserExperimentalFlag().getFlagValue()) {
                return Set.of();
            }

            if (!target.hasPermission(Job.DELETE)) {
                return Set.of();
            }

            return Set.of(new DeleteAction(target.getPronoun(), target.getDisplayName()));
        }
    }

    /**
     * Factory that contributes a {@link DeleteAction} to every {@link Run} the current
     * user has {@link Run#DELETE} permission on when the new run page experimental flag
     * is enabled.
     */
    @Extension(ordinal = 80)
    @Restricted(Beta.class)
    public static final class RunFactory extends TransientActionFactory<Run> {

        @Override
        public Class<Run> type() {
            return Run.class;
        }

        @Override
        public Collection<? extends Action> createFor(Run target) {
            Boolean newBuildPageEnabled = new NewBuildPageUserExperimentalFlag().getFlagValue();

            // This condition can be removed when the flag has been removed
            if (!newBuildPageEnabled) {
                return Set.of();
            }

            if (!target.hasPermission(Run.DELETE) || target.isKeepLog()) {
                return Set.of();
            }

            return Set.of(new DeleteAction("Build", target.getDisplayName()));
        }
    }
}
