/*
 * The MIT License
 *
 * Copyright (c) 2026, Markus Winter
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

package jenkins.model.agent;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.slaves.SlaveComputer;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewAgentPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import jenkins.model.menu.Semantic;
import jenkins.model.menu.event.Event;
import jenkins.model.menu.event.JavaScriptEvent;

@Extension
public class DisconnectAgentAction extends TransientActionFactory<SlaveComputer> {

    @Override
    public Class<SlaveComputer> type() {
        return SlaveComputer.class;
    }

    @NonNull
    @Override
    public Collection<? extends Action> createFor(@NonNull SlaveComputer target) {
        if (!target.hasPermission(Computer.DISCONNECT)) {
            return Set.of();
        }

        return Set.of(new Action() {
            @Override
            public String getDisplayName() {
                return Messages.DisconnectAgentAction_Title();
            }

            @Override
            public String getIconFileName() {
                return target.getChannel() != null ? "symbol-disconnect" : null;
            }

            @Override
            public Group getGroup() {
                return Group.FIRST_IN_MENU;
            }

            @Override
            public String getUrlName() {
                return "disconnect";
            }

            @Override
            public Event getEvent() {
                boolean newAgentPage = new NewAgentPageUserExperimentalFlag().getFlagValue();
                if (newAgentPage) {
                    return JavaScriptEvent.of(Map.of("type", "dialog-opener", "hide-close-button", "true", "dialog-url", "disconnectDialog"), "");
                }
                return Action.super.getEvent();
            }

            @Override
            public Semantic getSemantic() {
                return Semantic.DESTRUCTIVE;
            }
        });
    }
}
