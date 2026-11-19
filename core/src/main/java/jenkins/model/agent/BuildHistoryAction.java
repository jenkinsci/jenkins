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
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;

@Extension
public class BuildHistoryAction extends TransientActionFactory<Computer> {

    @Override
    public Class<Computer> type() {
        return Computer.class;
    }

    @NonNull
    @Override
    public Collection<? extends Action> createFor(@NonNull Computer target) {

        return Set.of(new Action() {
            @Override
            public String getIconFileName() {
                return "symbol-build-history";
            }

            @Override
            public String getDisplayName() {
                return Messages.BuildHistoryAction_Title();
            }

            @Override
            public String getUrlName() {
                return "builds";
            }

            @Override
            public Group getGroup() {
                return Group.FIRST_IN_MENU;
            }
        });
    }

}
