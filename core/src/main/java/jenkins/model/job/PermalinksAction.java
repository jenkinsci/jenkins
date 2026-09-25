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
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.Run;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewJobPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.DropdownEvent;
import jenkins.model.menu.event.Event;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * App bar action that exposes the available job permalinks in a dropdown.
 *
 * <p>Only permalinks that currently resolve to a build are shown in the menu.
 *
 * @since 2.564
 */
@Restricted(Beta.class)
public final class PermalinksAction implements Action {

    private final List<Action> permalinks;

    PermalinksAction(Job<?, ?> target) {
        permalinks = target.getPermalinks().stream()
                .map(permalink -> new PermalinkEntryAction(target, permalink))
                .filter(permalink -> permalink.getIconFileName() != null)
                .map(permalink -> (Action) permalink)
                .toList();
    }

    @Override
    public String getIconFileName() {
        if (permalinks.isEmpty()) {
            return null;
        }

        return "symbol-link";
    }

    @Override
    public String getDisplayName() {
        return Messages.PermalinksAction_Title();
    }

    @Override
    public String getUrlName() {
        return null;
    }

    @Override
    public Group getGroup() {
        return Group.of(Integer.MAX_VALUE - 1);
    }

    @Override
    public Event getEvent() {
        return DropdownEvent.of(permalinks);
    }

    @Override
    public boolean isVisibleInContextMenu() {
        return false;
    }

    private static final class PermalinkEntryAction implements Action {

        private final Permalink permalink;
        private final Run<?, ?> build;

        PermalinkEntryAction(Job<?, ?> target, Permalink permalink) {
            this.permalink = permalink;
            this.build = permalink.resolve(target);
        }

        @Override
        public String getIconFileName() {
            if (build == null) {
                return null;
            }

            return "symbol-link";
        }

        @Override
        public String getDisplayName() {
            return permalink.getDisplayName();
        }

        @Override
        public String getDescription() {
            return build.getDisplayName();
        }

        @Override
        public String getUrlName() {
            return permalink.getId();
        }
    }

    @Extension
    @Restricted(Beta.class)
    public static final class Factory extends TransientActionFactory<Job> {

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

            return Set.of(new PermalinksAction(target));
        }
    }
}
