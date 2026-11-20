package jenkins.model.job;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import java.util.Collection;
import java.util.List;
import jenkins.model.TransientActionFactory;
import jenkins.model.experimentalflags.UserExperimentalFlag;
import jenkins.model.menu.Group;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

public class BuildTimeTrendAction implements Action {
    private final Job<?, ?> target;

    BuildTimeTrendAction(Job<?, ?> target) {
        this.target = target;
    }

    @Override
    public String getIconFileName() {
        // Hide from sidepanel if the user has not enabled the new job page, otherwise show a graph icon.
        if (Boolean.TRUE.equals(
                UserExperimentalFlag.getFlagValueForCurrentUser("jenkins.model.experimentalflags.NewJobPageUserExperimentalFlag"))) {
            return "symbol-analytics";
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Build Time Trend";
    }

    @Override
    public String getUrlName() {
        return "buildTimeTrend";
    }

    @Override
    public Group getGroup() {
        return Group.FIRST_IN_MENU;
    }

    public Job<?, ?> getObject() {
        return target;
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
            return List.of(new BuildTimeTrendAction(target));
        }
    }
}
