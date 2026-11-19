/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package jenkins.model;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Item;
import hudson.security.AccessControlled;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import jenkins.model.menu.Group;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class RenameAction implements Action {

    @Override
    public String getIconFileName() {
        return "symbol-edit";
    }

    @Override
    public String getDisplayName() {
        return "Rename";
    }

    @Override
    public String getUrlName() {
        return "confirm-rename";
    }

    @Override
    public Group getGroup() {
        return Group.FIRST_IN_MENU;
    }

    @Extension(ordinal = 70)
    public static class TransientActionFactoryImpl extends TransientActionFactory<AbstractItem> {

        @Override
        public Class<AbstractItem> type() {
            return AbstractItem.class;
        }

        @Override
        public Collection<? extends Action> createFor(AbstractItem target) {
            if (!canRename(target)) {
                return Collections.emptyList();
            }

            return Set.of(new RenameAction());
        }

        private static boolean canRename(AbstractItem target) {
            if (!target.isNameEditable()) {
                return false;
            }

            if (target.hasPermission(Item.CONFIGURE)) {
                return true;
            }

            return target.getParent() instanceof AccessControlled parent
                    && parent.hasPermission(Item.CREATE)
                    && target.hasPermission(Item.DELETE);
        }
    }
}
