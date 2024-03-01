package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.DropdownAction;
import jenkins.model.menu.event.LinkAction;

public class AtomFeedMenuItem implements Action {

    @Override
    public String getDisplayName() {
        return "Atom feed";
    }

    @Override
    public String getIconFileName() {
        return "symbol-rss";
    }

    @Override
    public Group getGroup() {
        return Group.IN_MENU;
    }

    @Override
    public jenkins.model.menu.event.Action getAction() {
        return DropdownAction.of(
                new Action() {
                    @Override
                    public String getIconFileName() {
                        return "symbol-rss";
                    }

                    @Override
                    public String getDisplayName() {
                        return "All";
                    }

                    @Override
                    public jenkins.model.menu.event.Action getAction() {
                        return LinkAction.of("rssAll");
                    }
                },
                new Action() {
                    @Override
                    public String getIconFileName() {
                        return "symbol-rss";
                    }

                    @Override
                    public String getDisplayName() {
                        return "Failures";
                    }

                    @Override
                    public jenkins.model.menu.event.Action getAction() {
                        return LinkAction.of("rssFailed");
                    }
                },
                new Action() {
                    @Override
                    public String getIconFileName() {
                        return "symbol-rss";
                    }

                    @Override
                    public String getDisplayName() {
                        return "Latest builds";
                    }

                    @Override
                    public jenkins.model.menu.event.Action getAction() {
                        return LinkAction.of("latestBuilds");
                    }
                }
        );
    }

    @Extension
    public static class TransientActionFactoryImpl extends TransientActionFactory<View> {

        @Override
        public Class<View> type() {
            return View.class;
        }

        @Override
        public Collection<? extends Action> createFor(View target) {
            return Set.of(new AtomFeedMenuItem());
        }
    }
}
