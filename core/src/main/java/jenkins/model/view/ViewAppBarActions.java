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

import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.security.AccessControlled;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;
import jenkins.model.menu.Group;
import jenkins.model.menu.action.DeleteAction;
import jenkins.model.menu.event.DialogEvent;
import jenkins.model.menu.event.DropdownEvent;
import jenkins.model.menu.event.Event;
import jenkins.model.menu.event.LinkEvent;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * The built-in app bar actions for a {@link View}, its owning item (e.g. a folder) and the Jenkins root.
 * Each method returns actions whose URLs are relative to that object.
 */
@Restricted(NoExternalUse.class)
public final class ViewAppBarActions {

    private ViewAppBarActions() {}

    public static List<Action> createFor(View view) {
        ViewGroup owner = view.getOwner();
        List<Action> actions = new ArrayList<>();

        ItemGroup<?> itemGroup = owner.getItemGroup();
        if (itemGroup instanceof ModifiableTopLevelItemGroup
                && itemGroup instanceof AccessControlled accessControlled
                && accessControlled.hasPermission(View.getItemCreatePermission())) {
            actions.add(new LinkAction(Messages.ViewAppBarActions_NewItem(view.getNewPronoun()), "symbol-add", "newJob", Group.FIRST_IN_APP_BAR));
        }

        if (view.isEditable() && view.hasPermission(View.CONFIGURE)) {
            Group group = owner instanceof AbstractItem ? Group.FIRST_IN_MENU : Group.IN_APP_BAR;
            actions.add(new LinkAction(Messages.ViewAppBarActions_EditView(), "symbol-settings", "configure", group));
        }

        actions.add(new LinkAction(Messages.ViewAppBarActions_BuildHistory(), "symbol-build-history", "builds", Group.IN_MENU));
        actions.add(new LinkAction(Messages.ViewAppBarActions_AtomFeed(), "symbol-rss", null, Group.IN_MENU) {
            @Override
            public Event getEvent() {
                return DropdownEvent.of(List.of(
                        new LinkAction(Messages.ViewAppBarActions_AllBuilds(), "symbol-rss", "rssAll", Group.IN_MENU),
                        new LinkAction(Messages.ViewAppBarActions_FailedBuilds(), "symbol-rss", "rssFailed", Group.IN_MENU),
                        new LinkAction(Messages.ViewAppBarActions_LatestBuilds(), "symbol-rss", "rssLatest", Group.IN_MENU)));
            }

            @Override
            public boolean isVisibleInContextMenu() {
                return false;
            }
        });
        actions.add(new LinkAction(Messages.ViewAppBarActions_Legend(), "symbol-information-circle", null, Group.IN_MENU) {
            @Override
            public Event getEvent() {
                return DialogEvent.of("legend");
            }

            @Override
            public boolean isVisibleInContextMenu() {
                return false;
            }
        });

        if (owner.canDelete(view) && view.hasPermission(View.DELETE)) {
            actions.add(new DeleteAction("View", view.getDisplayName()));
        }

        return actions;
    }

    public static List<Action> createFor(AbstractItem item) {
        List<Action> actions = new ArrayList<>();

        if (item.hasPermission(Item.CONFIGURE)) {
            actions.add(new LinkAction(Messages.ViewAppBarActions_Configure(), "symbol-settings", "configure", Group.IN_APP_BAR));
        } else if (item.hasPermission(Item.EXTENDED_READ)) {
            actions.add(new LinkAction(Messages.ViewAppBarActions_ViewConfiguration(), "symbol-settings", "configure", Group.IN_APP_BAR));
        }

        if (item.hasPermission(Item.DELETE)) {
            actions.add(new DeleteAction(item.getPronoun(), item.getDisplayName()));
        }

        return actions;
    }

    public static List<Action> createForRoot() {
        Jenkins jenkins = Jenkins.get();

        if (!jenkins.hasPermission(Jenkins.READ) || !jenkins.getFingerprintMap().isReady()) {
            return List.of();
        }

        return List.of(
                new LinkAction(Messages.ViewAppBarActions_ProjectRelationship(), "symbol-project-relationship", "projectRelationship", Group.IN_MENU),
                new LinkAction(Messages.ViewAppBarActions_CheckFileFingerprint(), "symbol-fingerprint", "fingerprintCheck", Group.IN_MENU));
    }

    private static class LinkAction implements Action {

        private final String displayName;

        private final String icon;

        private final String urlName;

        private final Group group;

        LinkAction(String displayName, String icon, String urlName, Group group) {
            this.displayName = displayName;
            this.icon = icon;
            this.urlName = urlName;
            this.group = group;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getIconFileName() {
            return icon;
        }

        @Override
        public String getUrlName() {
            return urlName;
        }

        @Override
        public Group getGroup() {
            return group;
        }

        @Override
        public Event getEvent() {
            return LinkEvent.of(urlName);
        }
    }
}
