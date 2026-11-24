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

package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.TransientViewActionFactory;
import hudson.model.View;
import java.util.List;
import jenkins.model.experimentalflags.NewDashboardPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.DialogEvent;
import jenkins.model.menu.event.Event;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * App bar action that opens the icon legend in a dialog.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public final class LegendAction implements Action {

    @Override
    public String getDisplayName() {
        return Messages.LegendAction_Title();
    }

    @Override
    public String getIconFileName() {
        return "symbol-information-circle";
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
        return DialogEvent.of("legend");
    }

    @Override
    public boolean isVisibleInContextMenu() {
        return false;
    }

    @Extension
    @Restricted(Beta.class)
    public static final class Factory extends TransientViewActionFactory {

        @Override
        public List<Action> createFor(View target) {
            // This condition can be removed when the flag has been removed
            if (!new NewDashboardPageUserExperimentalFlag().getFlagValue()) {
                return List.of();
            }

            return List.of(new LegendAction());
        }
    }
}
