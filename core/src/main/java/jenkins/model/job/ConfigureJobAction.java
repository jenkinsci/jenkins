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
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * App bar action that links to the configuration page for a {@link Job}.
 *
 * <p>The display name is adjusted depending on whether the user has {@link Job#CONFIGURE}
 * or only {@link Job#EXTENDED_READ}.
 *
 * @since 2.560
 */
@Restricted(Beta.class)
public final class ConfigureJobAction implements Action {

    private final Job<?, ?> target;

    ConfigureJobAction(Job<?, ?> target) {
        this.target = target;
    }

    @Override
    public String getDisplayName() {
        return target.hasPermission(Job.CONFIGURE)
                ? Messages.ConfigureJobAction_Title()
                : Messages.ConfigureJobAction_ViewConfiguration();
    }

    @Override
    public String getIconFileName() {
        return "symbol-settings";
    }

    @Override
    public Group getGroup() {
        return Group.IN_APP_BAR;
    }

    @Override
    public String getUrlName() {
        return "configure";
    }

    @Extension(ordinal = 90)
    @Restricted(Beta.class)
    public static final class Factory extends TransientActionFactory<Job> {

        @Override
        public Class<Job> type() {
            return Job.class;
        }

        @Override
        public Collection<? extends Action> createFor(Job target) {
            if (!target.hasPermission(Job.CONFIGURE) && !target.hasPermission(Job.EXTENDED_READ)) {
                return Set.of();
            }

            return Set.of(new ConfigureJobAction(target));
        }
    }
}
