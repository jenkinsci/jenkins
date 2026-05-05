package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.NewDashboardPageUserExperimentalFlag;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.DropdownEvent;
import jenkins.model.menu.event.Event;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * App bar action that exposes RSS feeds for a {@link View}.
 *
 * <p>This action opens a dropdown with links to the all builds, latest builds,
 * and failed builds feeds.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public final class RssAction implements Action {

    @Override
    public String getDisplayName() {
        return Messages.RssAction_DisplayName();
    }

    @Override
    public String getIconFileName() {
        return "symbol-rss";
    }

    @Override
    public Group getGroup() {
        return Group.of(Integer.MAX_VALUE - 1);
    }

    @Override
    public String getUrlName() {
        return "";
    }

    @Override
    public Event getEvent() {
        return DropdownEvent.of(List.of(new Action() {
            @Override
            public String getIconFileName() {
                return "symbol-list";
            }

            @Override
            public String getDisplayName() {
                return Messages.RssAction_All_DisplayName();
            }

            @Override
            public String getUrlName() {
                return "rssAll";
            }
        }, new Action() {
            @Override
            public String getIconFileName() {
                return "symbol-clock";
            }

            @Override
            public String getDisplayName() {
                return Messages.RssAction_LatestBuilds_DisplayName();
            }

            @Override
            public String getUrlName() {
                return "rssLatest";
            }
        }, new Action() {
            @Override
            public String getIconFileName() {
                return "symbol-close-circle";
            }

            @Override
            public String getDisplayName() {
                return Messages.RssAction_Failures_DisplayName();
            }

            @Override
            public String getUrlName() {
                return "rssFailed";
            }
        }));
    }

    @Extension
    @Restricted(Beta.class)
    public static final class Factory extends TransientActionFactory<View> {

        @Override
        public Class<View> type() {
            return View.class;
        }

        @Override
        public Collection<? extends Action> createFor(View target) {
            Boolean newDashboardPageEnabled = new NewDashboardPageUserExperimentalFlag().getFlagValue();

            // This condition can be removed when the flag has been removed
            if (!newDashboardPageEnabled) {
                return Set.of();
            }

            return Set.of(new RssAction());
        }
    }
}
